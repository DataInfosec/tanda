//! UniFFI-compatible records, enums, and errors used by the Kotlin boundary.

use std::error::Error;
use std::fmt::{self, Display, Formatter};

use crate::sdk::{
    AttendanceEnrollmentResult, AttendanceIdentifyResult, AttendanceSyncReport,
    AttendanceSyncState, DuplicateEnrollmentMatch, EnrollmentAttempt, EnrollmentBatch,
    EnrollmentBatchAuthorization, EnrollmentReadiness, EnrollmentRejectionReason, EnrollmentReport,
    GalleryStats, IdentifyRetryReason, SdkError, SdkErrorCode, SubjectEnrollmentAuthorization,
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

/// Observable gallery synchronization state.
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

impl From<AttendanceSyncState> for MobileSyncState {
    fn from(state: AttendanceSyncState) -> Self {
        match state {
            AttendanceSyncState::Ready => Self::Ready,
            AttendanceSyncState::Offline => Self::Offline,
            AttendanceSyncState::WriterRevoked => Self::WriterRevoked,
            AttendanceSyncState::Quarantined => Self::Quarantined,
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
    /// Subjects currently available to 1:N matching.
    pub indexed_subjects: u64,
    /// This device's submissions awaiting a server decision.
    pub pending_enrollments: u64,
}

impl From<AttendanceSyncReport> for MobileSyncReport {
    fn from(report: AttendanceSyncReport) -> Self {
        Self {
            state: report.state.into(),
            frames_synced: report.frames_synced as u64,
            gallery_revision: report.gallery_revision,
            indexed_subjects: report.indexed_subjects as u64,
            pending_enrollments: report.pending_enrollments as u64,
        }
    }
}

/// Current immutable gallery matcher summary.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileGallerySummary {
    /// Server-authoritative gallery identifier.
    pub gallery_id: String,
    /// Canonical application revision represented by the matcher.
    pub gallery_revision: u64,
    /// Indexed fingerprint record count.
    pub records: u64,
    /// Distinct indexed subject count.
    pub subjects: u64,
}

impl From<GalleryStats> for MobileGallerySummary {
    fn from(stats: GalleryStats) -> Self {
        Self {
            gallery_id: stats.gallery_id,
            gallery_revision: stats.gallery_revision,
            records: stats.records as u64,
            subjects: stats.subjects as u64,
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
    /// The first two subjects were too close to choose safely.
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
    /// One subject passed candidate and geometric verification.
    Match {
        /// Canonical application subject identifier.
        subject_id: String,
        /// Finger record that produced the accepted match.
        record_id: String,
        /// Gallery searched for this decision.
        gallery_id: String,
        /// Immutable gallery revision searched for this decision.
        gallery_revision: u64,
        /// Biometric modality used for this decision.
        modality: String,
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

impl From<AttendanceIdentifyResult> for MobileIdentifyOutcome {
    fn from(result: AttendanceIdentifyResult) -> Self {
        match result {
            AttendanceIdentifyResult::Match(hit) => Self::Match {
                subject_id: hit.subject_id,
                record_id: hit.record_id,
                gallery_id: hit.gallery_id,
                gallery_revision: hit.gallery_revision,
                modality: hit.modality,
                score: hit.score,
                verification_score: hit.verification_score,
            },
            AttendanceIdentifyResult::Retry(retry) => Self::Retry {
                reason: retry.reason.into(),
                best_score: retry.best_hit.as_ref().map(|hit| hit.score),
                best_verification_score: retry.best_hit.as_ref().map(|hit| hit.verification_score),
            },
        }
    }
}

/// Existing subject found by the duplicate-enrollment guard.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileDuplicateMatch {
    /// Existing subject identifier.
    pub subject_id: String,
    /// Blended match score.
    pub score: f32,
    /// Geometric verification score.
    pub verification_score: f32,
}

impl From<DuplicateEnrollmentMatch> for MobileDuplicateMatch {
    fn from(value: DuplicateEnrollmentMatch) -> Self {
        Self {
            subject_id: value.subject_id,
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
    /// Subject already has the configured template maximum.
    MaxTemplatesForSubject {
        /// Configured per-subject template maximum.
        max_templates: u64,
    },
    /// The scan belongs to another enrolled subject.
    DuplicateOfOtherSubject {
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
            EnrollmentRejectionReason::MaxTemplatesForSubject { max_templates } => {
                Self::MaxTemplatesForSubject {
                    max_templates: max_templates as u64,
                }
            }
            EnrollmentRejectionReason::DuplicateOfOtherSubject { duplicate } => {
                Self::DuplicateOfOtherSubject {
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
    /// Subject identifier supplied by the caller.
    pub subject_id: String,
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
            subject_id: attempt.subject_id,
            quality: attempt.quality,
            accepted: attempt.accepted,
            rejection: attempt.rejection.map(Into::into),
        }
    }
}

/// Capture-level report for one subject enrollment operation.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentReport {
    /// Gallery identifier.
    pub gallery_id: String,
    /// Templates accepted into the durable submission.
    pub accepted_records: u64,
    /// Distinct subjects represented by the operation.
    pub accepted_subjects: u64,
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
            accepted_subjects: report.accepted_subjects as u64,
            rejected_captures: report.rejected_captures as u64,
            attempts: report.attempts.into_iter().map(Into::into).collect(),
        }
    }
}

/// Online server authorization for opening one group-enrollment organizer.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileEnrollmentBatchAuthorization {
    /// Opaque server authorization identifier.
    pub authorization_id: String,
    /// Canonical administrator identity.
    pub performed_by: String,
    /// Physical writer instance.
    pub device_instance_id: String,
    /// Authorized fixed-population gallery.
    pub gallery_id: String,
    /// RFC3339 authorization expiry.
    pub authorization_expires_at: String,
}

impl From<MobileEnrollmentBatchAuthorization> for EnrollmentBatchAuthorization {
    fn from(value: MobileEnrollmentBatchAuthorization) -> Self {
        Self {
            authorization_id: value.authorization_id,
            performed_by: value.performed_by,
            device_instance_id: value.device_instance_id,
            gallery_id: value.gallery_id,
            authorization_expires_at: value.authorization_expires_at,
        }
    }
}

/// Online server authorization for enrolling exactly one subject.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileSubjectEnrollmentAuthorization {
    /// Opaque, idempotent operation identifier.
    pub enrollment_operation_id: String,
    /// Canonical administrator identity.
    pub performed_by: String,
    /// Physical writer instance.
    pub device_instance_id: String,
    /// Authorized fixed-population gallery.
    pub gallery_id: String,
    /// Canonical subject authorized for capture.
    pub subject_id: String,
    /// Optional group organizer.
    pub batch_id: Option<String>,
    /// RFC3339 authorization expiry.
    pub authorization_expires_at: String,
}

impl From<MobileSubjectEnrollmentAuthorization> for SubjectEnrollmentAuthorization {
    fn from(value: MobileSubjectEnrollmentAuthorization) -> Self {
        Self {
            enrollment_operation_id: value.enrollment_operation_id,
            performed_by: value.performed_by,
            device_instance_id: value.device_instance_id,
            gallery_id: value.gallery_id,
            subject_id: value.subject_id,
            batch_id: value.batch_id,
            authorization_expires_at: value.authorization_expires_at,
        }
    }
}

/// Stable local readiness state checked before requesting a fingerprint capture.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MobileEnrollmentReadiness {
    /// Writer and subject membership are locally ready.
    Ready,
    /// Synchronize the gallery before requesting capture.
    GallerySyncRequired,
    /// Server synchronization revoked this writer instance.
    WriterRevoked,
    /// Synchronized rows failed validation.
    Quarantined,
}

impl From<EnrollmentReadiness> for MobileEnrollmentReadiness {
    fn from(value: EnrollmentReadiness) -> Self {
        match value {
            EnrollmentReadiness::Ready => Self::Ready,
            EnrollmentReadiness::GallerySyncRequired => Self::GallerySyncRequired,
            EnrollmentReadiness::WriterRevoked => Self::WriterRevoked,
            EnrollmentReadiness::Quarantined => Self::Quarantined,
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
    pub device_instance_id: String,
    /// Canonical administrator who opened the batch.
    pub performed_by: String,
    /// Opaque server-issued batch authorization.
    pub authorization_id: String,
    /// RFC3339 authorization expiry.
    pub authorization_expires_at: String,
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
            device_instance_id: batch.device_instance_id,
            performed_by: batch.performed_by,
            authorization_id: batch.authorization_id,
            authorization_expires_at: batch.authorization_expires_at,
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

impl From<AttendanceEnrollmentResult> for MobileEnrollmentResult {
    fn from(result: AttendanceEnrollmentResult) -> Self {
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
            MobileSyncState::from(AttendanceSyncState::Ready),
            MobileSyncState::Ready
        );
        assert_eq!(
            MobileSyncState::from(AttendanceSyncState::Offline),
            MobileSyncState::Offline
        );
        assert_eq!(
            MobileSyncState::from(AttendanceSyncState::WriterRevoked),
            MobileSyncState::WriterRevoked
        );
        assert_eq!(
            MobileSyncState::from(AttendanceSyncState::Quarantined),
            MobileSyncState::Quarantined
        );
    }
}
