//! Enrollment policy and capture-level outcomes.
//!
//! Durable enrollment state belongs to the SDK-owned gallery database in
//! `attendance`. This module contains only policy and result values shared by the
//! Rust API, UniFFI facade, and server artifact validator.

use serde::{Deserialize, Serialize};

#[cfg(any(test, feature = "attendance-libsql", feature = "server-ffi"))]
use super::error::{SdkError, SdkResult};
use super::index::{RerankConfig, SearchConfig};
#[cfg(any(test, feature = "attendance-libsql", feature = "server-ffi"))]
use super::limits::SdkLimits;

/// Balanced default minimum enrollment quality.
pub const DEFAULT_ENROLLMENT_MIN_QUALITY: u8 = 65;

/// Enrollment acceptance and cross-subject duplicate policy.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EnrollmentConfig {
    /// Minimum quality accepted for a template.
    pub min_quality: u8,
    /// Maximum templates retained for one subject artifact.
    pub max_templates_per_subject: usize,
    /// Cross-subject duplicate prevention settings.
    pub duplicate: DuplicateCheckConfig,
}

impl Default for EnrollmentConfig {
    fn default() -> Self {
        let search = SearchConfig {
            top_k: 8,
            rerank: RerankConfig {
                candidate_limit: 128,
                ..RerankConfig::default()
            },
        };
        Self {
            min_quality: DEFAULT_ENROLLMENT_MIN_QUALITY,
            max_templates_per_subject: 2,
            duplicate: DuplicateCheckConfig {
                enabled: true,
                min_score: 0.30,
                min_verification_score: 0.20,
                search,
            },
        }
    }
}

impl EnrollmentConfig {
    /// Set the minimum accepted capture quality.
    pub fn with_min_quality(mut self, min_quality: u8) -> Self {
        self.min_quality = min_quality;
        self
    }

    /// Set the maximum number of templates retained for one subject.
    pub fn with_max_templates_per_subject(mut self, maximum: usize) -> Self {
        self.max_templates_per_subject = maximum;
        self
    }

    #[cfg(any(test, feature = "attendance-libsql", feature = "server-ffi"))]
    pub(crate) fn validate(self, limits: SdkLimits) -> SdkResult<Self> {
        if self.min_quality > 100 {
            return Err(SdkError::invalid_input("min_quality must be in 0..=100"));
        }
        if self.max_templates_per_subject == 0
            || self.max_templates_per_subject > limits.max_records.min(16)
        {
            return Err(SdkError::invalid_input(
                "max_templates_per_subject must be in 1..=16",
            ));
        }
        for (label, value) in [
            ("duplicate min_score", self.duplicate.min_score),
            (
                "duplicate min_verification_score",
                self.duplicate.min_verification_score,
            ),
        ] {
            if !value.is_finite() || !(0.0..=1.0).contains(&value) {
                return Err(SdkError::invalid_input(format!(
                    "{label} must be finite and in 0.0..=1.0"
                )));
            }
        }

        Ok(self)
    }
}

/// Cross-subject duplicate prevention settings.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DuplicateCheckConfig {
    /// Whether duplicate prevention is active.
    pub enabled: bool,
    /// Minimum blended match score considered a duplicate.
    pub min_score: f32,
    /// Minimum geometric verification score considered a duplicate.
    pub min_verification_score: f32,
    /// Candidate-search and reranking settings.
    pub search: SearchConfig,
}

/// Result of one capture considered during enrollment.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EnrollmentAttempt {
    /// Subject identifier supplied by the caller.
    pub subject_id: String,
    /// SDK-generated UUIDv7 template record identifier when committed.
    pub record_id: Option<String>,
    /// Extracted quality when extraction succeeded.
    pub quality: Option<u8>,
    /// Whether this capture joined the committed submission.
    pub accepted: bool,
    /// Rejection details when the capture was not committed.
    pub rejection: Option<EnrollmentRejectionReason>,
}

/// Reason an enrollment capture was not committed.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case", deny_unknown_fields)]
pub enum EnrollmentRejectionReason {
    /// Capture bytes or extractor input were invalid.
    InvalidCapture {
        /// Diagnostic extraction or input-validation message.
        message: String,
    },
    /// Capture quality was below policy.
    LowQuality {
        /// Measured capture quality.
        quality: u8,
        /// Minimum configured enrollment quality.
        min_quality: u8,
    },
    /// The subject already reached the configured template count.
    MaxTemplatesForSubject {
        /// Maximum templates retained for one subject.
        max_templates: usize,
    },
    /// The capture matched another enrolled subject.
    DuplicateOfOtherSubject {
        /// Existing subject template that matched this capture.
        duplicate: DuplicateEnrollmentMatch,
    },
    /// An operation-level failure rolled back a previously accepted capture.
    NotCommitted {
        /// Diagnostic reason the capture was rolled back.
        message: String,
    },
}

/// Existing enrollment matched during duplicate prevention.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DuplicateEnrollmentMatch {
    /// Existing subject identifier.
    pub subject_id: String,
    /// Existing SDK template record identifier.
    pub record_id: String,
    /// Final blended match score.
    pub score: f32,
    /// Geometric verification score.
    pub verification_score: f32,
}

/// Capture-level outcome of one subject enrollment operation.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EnrollmentReport {
    /// Gallery that owns the submission.
    pub gallery_id: String,
    /// Templates accepted into the durable submission.
    pub accepted_records: usize,
    /// Distinct subjects represented by committed templates.
    pub accepted_subjects: usize,
    /// Rejected or rolled-back captures.
    pub rejected_captures: usize,
    /// Individual capture outcomes in input order.
    pub attempts: Vec<EnrollmentAttempt>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_policy_is_valid() {
        let policy = EnrollmentConfig::default()
            .validate(SdkLimits::default())
            .unwrap();
        assert_eq!(policy.min_quality, DEFAULT_ENROLLMENT_MIN_QUALITY);
        assert_eq!(policy.max_templates_per_subject, 2);
        assert!(policy.duplicate.enabled);
    }

    #[test]
    fn invalid_policy_is_rejected_at_initialization() {
        let error = EnrollmentConfig::default()
            .with_max_templates_per_subject(0)
            .validate(SdkLimits::default())
            .unwrap_err();
        assert_eq!(
            error.code(),
            super::super::error::SdkErrorCode::InvalidInput
        );
    }
}
