//! Validation and duplicate policy for one-student template artifacts.
//!
//! Both the SDK-owned libSQL repository and the server FFI adapter use this
//! module. Keeping artifact interpretation here prevents Go, Kotlin, and SQL
//! projection code from duplicating the private binary format.

use sha2::{Digest, Sha256};

use super::enrollment::{DuplicateCheckConfig, DuplicateEnrollmentMatch};
use super::error::{SdkError, SdkResult};
use super::extractor::ExtractorConfig;
use super::index::BiometricIndex;
use super::limits::SdkLimits;
use super::storage::{hex, validate_identifier};
use super::template::{DEFAULT_EXTRACTOR_PROFILE, TEMPLATE_FORMAT_VERSION, TemplateStore};

/// Metadata stored beside an opaque one-student template payload.
#[derive(Debug, Clone, Copy)]
pub struct TemplateArtifactRef<'a> {
    /// Student expected to own every encoded template.
    pub student_id: &'a str,
    /// Binary schema identifier.
    pub format_version: &'a str,
    /// Extraction profile identifier.
    pub extractor_profile: &'a str,
    /// Opaque SDK template bytes.
    pub payload: &'a [u8],
    /// `sha256:<lowercase hex>` digest of `payload`.
    pub checksum: &'a str,
}

/// Validate and decode one bounded student artifact.
pub fn decode_student_template_artifact(
    artifact: TemplateArtifactRef<'_>,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<TemplateStore> {
    validate_identifier("student_id", artifact.student_id)?;
    if artifact.format_version != TEMPLATE_FORMAT_VERSION {
        return Err(SdkError::invalid_format(format!(
            "unsupported template format {}",
            artifact.format_version
        )));
    }
    if artifact.extractor_profile != DEFAULT_EXTRACTOR_PROFILE {
        return Err(SdkError::invalid_format(format!(
            "unsupported extractor profile {}",
            artifact.extractor_profile
        )));
    }
    if template_payload_checksum(artifact.payload) != artifact.checksum {
        return Err(SdkError::integrity(format!(
            "template checksum mismatch for student {}",
            artifact.student_id
        )));
    }
    let decoded = TemplateStore::from_bytes_with_config(artifact.payload, extractor, limits)?;
    if decoded.single_user_id()? != artifact.student_id {
        return Err(SdkError::integrity(format!(
            "template ownership mismatch for student {}",
            artifact.student_id
        )));
    }

    Ok(decoded)
}

/// Find the strongest cross-student duplicate for a candidate artifact.
///
/// Existing templates are indexed once. Every candidate finger is queried
/// against that index, same-student hits are excluded, and the strongest hit
/// passing both candidate and geometric thresholds is returned.
pub fn find_school_duplicate(
    candidate: &TemplateStore,
    existing: &TemplateStore,
    config: DuplicateCheckConfig,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<Option<DuplicateEnrollmentMatch>> {
    if !config.enabled || existing.is_empty() {
        return Ok(None);
    }
    let candidate_student = candidate.single_user_id()?;
    let existing_templates = existing
        .templates()
        .into_iter()
        .filter(|template| template.record.user_id != candidate_student)
        .collect::<Vec<_>>();
    if existing_templates.is_empty() {
        return Ok(None);
    }
    let index = BiometricIndex::build_with_config(&existing_templates, extractor, limits)?;
    let mut strongest = None;
    for template in candidate.templates() {
        for hit in index.search_users(&template, config.search)? {
            if hit.score < config.min_score
                || hit.verification_score < config.min_verification_score
            {
                continue;
            }
            let replace = strongest
                .as_ref()
                .is_none_or(|current: &DuplicateEnrollmentMatch| hit.score > current.score);
            if replace {
                strongest = Some(DuplicateEnrollmentMatch {
                    student_id: hit.user_id,
                    record_id: hit.record_id,
                    score: hit.score,
                    verification_score: hit.verification_score,
                });
            }
        }
    }

    Ok(strongest)
}

/// Return the canonical checksum representation for a template payload.
pub fn template_payload_checksum(payload: &[u8]) -> String {
    format!("sha256:{}", hex(&Sha256::digest(payload)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::sdk::extractor::{ExtractedTemplate, FingerRecord, TemplateFeature};

    fn template(student_id: &str) -> ExtractedTemplate {
        ExtractedTemplate {
            record: FingerRecord {
                record_id: "record-1".to_owned(),
                user_id: student_id.to_owned(),
            },
            quality: 80,
            token_count: 1,
            tokens: vec![12],
            features: vec![TemplateFeature {
                x: 10,
                y: 10,
                orientation: 1,
                contrast: 3,
                coherence: 80,
                kind: 1,
            }],
        }
    }

    #[test]
    fn artifact_validation_checks_checksum_and_ownership() {
        let store = TemplateStore::from_templates(vec![template("student-1")]).unwrap();
        let payload = store.to_bytes().unwrap();
        let artifact = TemplateArtifactRef {
            student_id: "student-1",
            format_version: TEMPLATE_FORMAT_VERSION,
            extractor_profile: DEFAULT_EXTRACTOR_PROFILE,
            payload: &payload,
            checksum: &template_payload_checksum(&payload),
        };
        assert_eq!(
            decode_student_template_artifact(
                artifact,
                ExtractorConfig::default(),
                SdkLimits::default()
            )
            .unwrap()
            .single_user_id()
            .unwrap(),
            "student-1"
        );

        let wrong_owner = TemplateArtifactRef {
            student_id: "student-2",
            ..artifact
        };
        assert_eq!(
            decode_student_template_artifact(
                wrong_owner,
                ExtractorConfig::default(),
                SdkLimits::default()
            )
            .unwrap_err()
            .code(),
            super::super::error::SdkErrorCode::Integrity
        );
    }
}
