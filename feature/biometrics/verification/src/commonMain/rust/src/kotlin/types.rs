//! UniFFI-compatible records, enums, and errors used by the Kotlin boundary.

use std::error::Error;
use std::fmt::{self, Display, Formatter};

use crate::sdk::{
    CampusEnrollmentResult, CampusSyncReport, CampusSyncState, DuplicateEnrollmentMatch,
    EnrollmentAttempt, EnrollmentBatch, EnrollmentRejectionReason, EnrollmentReport, GalleryStats,
    IdentifyResult, IdentifyRetryReason, SdkError, SdkErrorCode,
};

/// Stable exception categories generated for Kotlin callers.
#[derive(Debug, uniffi::Error)]
pub enum MobileSdkError {
    /// Caller input is invalid.
    InvalidInput {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Encoded biometric data is malformed.
    InvalidFormat {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Synchronized or encoded data failed an integrity check.
    Integrity {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Local or synchronized state conflicts with the operation.
    Conflict {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Requested state does not exist.
    NotFound {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Another enrollment owner is already active.
    SessionActive {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Filesystem or stream I/O failed.
    Io {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// The embedded gallery database rejected an operation.
    Database {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Remote synchronization failed or is temporarily unavailable.
    Sync {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// The device database schema requires another SDK version.
    SchemaUnsupported {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// A configured size or record limit was exceeded.
    ResourceLimit {
        /// Human-readable diagnostic context.
        message: String,
    },
}

impl MobileSdkError {
    fn message(&self) -> &str {
        match self {
            Self::InvalidInput { message }
            | Self::InvalidFormat { message }
            | Self::Integrity { message }
            | Self::Conflict { message }
            | Self::NotFound { message }
            | Self::SessionActive { message }
            | Self::Io { message }
            | Self::Database { message }
            | Self::Sync { message }
            | Self::SchemaUnsupported { message }
            | Self::ResourceLimit { message } => message,
        }
    }
}

impl Display for MobileSdkError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.message())
    }
}

impl Error for MobileSdkError {}

impl From<SdkError> for MobileSdkError {
    fn from(error: SdkError) -> Self {
        let message = error.to_string();
        match error.code() {
            SdkErrorCode::InvalidInput => Self::InvalidInput { message },
            SdkErrorCode::InvalidFormat => Self::InvalidFormat { message },
            SdkErrorCode::Integrity => Self::Integrity { message },
            SdkErrorCode::Conflict => Self::Conflict { message },
            SdkErrorCode::NotFound => Self::NotFound { message },
            SdkErrorCode::SessionActive => Self::SessionActive { message },
            SdkErrorCode::Io => Self::Io { message },
            SdkErrorCode::Database => Self::Database { message },
            SdkErrorCode::Sync => Self::Sync { message },
            SdkErrorCode::SchemaUnsupported => Self::SchemaUnsupported { message },
            SdkErrorCode::ResourceLimit => Self::ResourceLimit { message },
        }
    }
}

/// Observable class-gallery synchronization state.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MobileSyncState {
    /// The last synchronization and validation completed.
    Ready,
    /// The verified local gallery is available without a network connection.
    Offline,
    /// The server no longer recognizes this device as the enrollment writer.
    WriterRevoked,
    /// Synchronized rows failed validation and enrollment is blocked.
    Quarantined,
}

impl From<CampusSyncState> for MobileSyncState {
    fn from(state: CampusSyncState) -> Self {
        match state {
            CampusSyncState::Ready => Self::Ready,
            CampusSyncState::Offline => Self::Offline,
            CampusSyncState::WriterRevoked => Self::WriterRevoked,
            CampusSyncState::Quarantined => Self::Quarantined,
        }
    }
}

/// Result of one explicit libSQL synchronization attempt.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileSyncReport {
    /// State after synchronization and row validation.
    pub state: MobileSyncState,
    /// Physical 4 KiB frames transferred by libSQL.
    pub frames_synced: u64,
    /// Server application revision represented by the matcher.
    pub gallery_revision: u64,
    /// Students currently available to 1:N matching.
    pub indexed_users: u64,
    /// This device's submissions awaiting a server decision.
    pub pending_enrollments: u64,
}

impl From<CampusSyncReport> for MobileSyncReport {
    fn from(report: CampusSyncReport) -> Self {
        Self {
            state: report.state.into(),
            frames_synced: report.frames_synced as u64,
            gallery_revision: report.gallery_revision,
            indexed_users: report.indexed_users as u64,
            pending_enrollments: report.pending_enrollments as u64,
        }
    }
}

/// Current immutable class matcher summary.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileGallerySummary {
    /// Server-authoritative class gallery identifier.
    pub gallery_id: String,
    /// Canonical application revision represented by the matcher.
    pub gallery_revision: u64,
    /// Indexed fingerprint record count.
    pub records: u64,
    /// Distinct indexed student count.
    pub users: u64,
}

impl From<GalleryStats> for MobileGallerySummary {
    fn from(stats: GalleryStats) -> Self {
        Self {
            gallery_id: stats.gallery_id,
            gallery_revision: stats.gallery_revision,
            records: stats.records as u64,
            users: stats.users as u64,
        }
    }
}

/// Reason that the clock-in UI should request another scan.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MobileRetryReason {
    /// Capture quality was below the query floor.
    LowQuality,
    /// The candidate index found no shared descriptors.
    NoCandidates,
    /// The strongest candidate failed the calibrated acceptance floor.
    WeakScore,
    /// The first two students were too close to choose safely.
    Ambiguous,
}

impl From<IdentifyRetryReason> for MobileRetryReason {
    fn from(reason: IdentifyRetryReason) -> Self {
        match reason {
            IdentifyRetryReason::LowQuality => Self::LowQuality,
            IdentifyRetryReason::NoCandidates => Self::NoCandidates,
            IdentifyRetryReason::WeakScore => Self::WeakScore,
            IdentifyRetryReason::Ambiguous => Self::Ambiguous,
        }
    }
}

/// Offline 1:N identification outcome.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum MobileIdentifyOutcome {
    /// One student passed candidate and geometric verification.
    Match {
        /// Application student identifier.
        student_id: String,
        /// Blended candidate and verification score in `0.0..=1.0`.
        score: f32,
        /// Geometric minutiae verification score in `0.0..=1.0`.
        verification_score: f32,
    },
    /// The app should request another scan.
    Retry {
        /// Stable retry category.
        reason: MobileRetryReason,
        /// Best observed score for optional diagnostics.
        best_score: Option<f32>,
        /// Best observed geometric score for optional diagnostics.
        best_verification_score: Option<f32>,
    },
}

impl From<IdentifyResult> for MobileIdentifyOutcome {
    fn from(result: IdentifyResult) -> Self {
        match result {
            IdentifyResult::Match(hit) => Self::Match {
                student_id: hit.user_id,
                score: hit.score,
                verification_score: hit.verification_score,
            },
            IdentifyResult::Retry(retry) => Self::Retry {
                reason: retry.reason.into(),
                best_score: retry.best_hit.as_ref().map(|hit| hit.score),
                best_verification_score: retry.best_hit.as_ref().map(|hit| hit.verification_score),
            },
        }
    }
}

/// Existing student found by the duplicate-enrollment guard.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileDuplicateMatch {
    /// Existing student identifier.
    pub student_id: String,
    /// Blended match score.
    pub score: f32,
    /// Geometric verification score.
    pub verification_score: f32,
}

impl From<DuplicateEnrollmentMatch> for MobileDuplicateMatch {
    fn from(value: DuplicateEnrollmentMatch) -> Self {
        Self {
            student_id: value.student_id,
            score: value.score,
            verification_score: value.verification_score,
        }
    }
}

/// Capture-level enrollment rejection returned to Android.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum MobileEnrollmentRejection {
    /// Capture bytes or extraction input were invalid.
    InvalidCapture {
        /// Human-readable extraction diagnostic.
        message: String,
    },
    /// Capture quality was below enrollment policy.
    LowQuality {
        /// Measured quality.
        quality: u8,
        /// Required quality.
        min_quality: u8,
    },
    /// Student already has the configured template maximum.
    MaxTemplatesForStudent {
        /// Configured per-student template maximum.
        max_templates: u64,
    },
    /// The scan belongs to another enrolled student.
    DuplicateOfOtherStudent {
        /// Existing enrollment that matched this scan.
        duplicate: MobileDuplicateMatch,
    },
    /// An operation-level failure rolled back the capture.
    NotCommitted {
        /// Human-readable rollback reason.
        message: String,
    },
}

impl From<EnrollmentRejectionReason> for MobileEnrollmentRejection {
    fn from(reason: EnrollmentRejectionReason) -> Self {
        match reason {
            EnrollmentRejectionReason::InvalidCapture { message } => {
                Self::InvalidCapture { message }
            }
            EnrollmentRejectionReason::LowQuality {
                quality,
                min_quality,
            } => Self::LowQuality {
                quality,
                min_quality,
            },
            EnrollmentRejectionReason::MaxTemplatesForStudent { max_templates } => {
                Self::MaxTemplatesForStudent {
                    max_templates: max_templates as u64,
                }
            }
            EnrollmentRejectionReason::DuplicateOfOtherStudent { duplicate } => {
                Self::DuplicateOfOtherStudent {
                    duplicate: duplicate.into(),
                }
            }
            EnrollmentRejectionReason::NotCommitted { message } => Self::NotCommitted { message },
        }
    }
}

/// Result of one capture considered during enrollment.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentAttempt {
    /// Student identifier supplied by the caller.
    pub student_id: String,
    /// Extracted quality when available.
    pub quality: Option<u8>,
    /// Whether the template joined the committed submission.
    pub accepted: bool,
    /// Rejection details when not accepted.
    pub rejection: Option<MobileEnrollmentRejection>,
}

impl From<EnrollmentAttempt> for MobileEnrollmentAttempt {
    fn from(attempt: EnrollmentAttempt) -> Self {
        Self {
            student_id: attempt.student_id,
            quality: attempt.quality,
            accepted: attempt.accepted,
            rejection: attempt.rejection.map(Into::into),
        }
    }
}

/// Capture-level report for one student enrollment operation.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentReport {
    /// Class gallery identifier.
    pub gallery_id: String,
    /// Templates accepted into the durable submission.
    pub accepted_records: u64,
    /// Distinct students represented by the operation.
    pub accepted_students: u64,
    /// Rejected captures.
    pub rejected_captures: u64,
    /// Individual capture outcomes.
    pub attempts: Vec<MobileEnrollmentAttempt>,
}

impl From<EnrollmentReport> for MobileEnrollmentReport {
    fn from(report: EnrollmentReport) -> Self {
        Self {
            gallery_id: report.gallery_id,
            accepted_records: report.accepted_records as u64,
            accepted_students: report.accepted_students as u64,
            rejected_captures: report.rejected_captures as u64,
            attempts: report.attempts.into_iter().map(Into::into).collect(),
        }
    }
}

/// Lifecycle state for a group-enrollment batch.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MobileEnrollmentBatchStatus {
    /// Captures may be added to the batch.
    Active,
    /// The operator completed the batch.
    Closed,
    /// The operator cancelled the batch.
    Cancelled,
}

/// Persisted group-enrollment batch that can resume after process restart.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileEnrollmentBatch {
    /// UUIDv7 batch identifier.
    pub id: String,
    /// Device that owns this resumable batch.
    pub device_id: String,
    /// Current lifecycle state.
    pub status: MobileEnrollmentBatchStatus,
    /// UTC timestamp at which capture began.
    pub started_at: String,
    /// UTC terminal timestamp, when complete.
    pub closed_at: Option<String>,
}

impl TryFrom<EnrollmentBatch> for MobileEnrollmentBatch {
    type Error = MobileSdkError;

    fn try_from(batch: EnrollmentBatch) -> Result<Self, Self::Error> {
        let status = match batch.status.as_str() {
            "active" => MobileEnrollmentBatchStatus::Active,
            "closed" => MobileEnrollmentBatchStatus::Closed,
            "cancelled" => MobileEnrollmentBatchStatus::Cancelled,
            value => {
                return Err(MobileSdkError::Integrity {
                    message: format!("unsupported enrollment batch status {value}"),
                });
            }
        };
        Ok(Self {
            id: batch.id,
            device_id: batch.device_id,
            status,
            started_at: batch.started_at,
            closed_at: batch.closed_at,
        })
    }
}

/// Durable enrollment submission result.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentResult {
    /// UUIDv7 submission identifier when a template was accepted.
    pub submission_id: Option<String>,
    /// Group batch identifier for group enrollment.
    pub batch_id: Option<String>,
    /// Capture-level outcomes.
    pub report: MobileEnrollmentReport,
}

impl From<CampusEnrollmentResult> for MobileEnrollmentResult {
    fn from(result: CampusEnrollmentResult) -> Self {
        Self {
            submission_id: result.submission_id,
            batch_id: result.batch_id,
            report: result.report.into(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_sync_state_has_a_mobile_representation() {
        assert_eq!(
            MobileSyncState::from(CampusSyncState::Ready),
            MobileSyncState::Ready
        );
        assert_eq!(
            MobileSyncState::from(CampusSyncState::Offline),
            MobileSyncState::Offline
        );
        assert_eq!(
            MobileSyncState::from(CampusSyncState::WriterRevoked),
            MobileSyncState::WriterRevoked
        );
        assert_eq!(
            MobileSyncState::from(CampusSyncState::Quarantined),
            MobileSyncState::Quarantined
        );
    }
}
