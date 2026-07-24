//! Android entry point for clock-in, enrollment, and gallery synchronization.
//!
//! A gallery is the active membership and accepted fingerprint-template dataset
//! for one fixed population. Each instance owns a local copy of one gallery.
//! Its gallery revision is the server-assigned
//! application revision of the canonical data represented by its matcher; it is
//! not a database frame count or an app-managed counter.
//!
//! An instance can operate as an attendance reader, which only identifies
//! subjects, or as the gallery's single enrollment writer, which may also create
//! durable enrollment submissions. The server decides which device is the
//! writer; the application reflects that assignment in its UI. This separation
//! allows many least-privilege clock-in devices while keeping unsynchronized
//! biometric changes on one recoverable device and avoiding competing
//! enrollments for the same gallery.
//!
//! # Why the SDK keeps local state
//!
//! Clock-in must continue through a network outage. The SDK therefore keeps a
//! private database and a verified in-memory matcher on the device:
//!
//! ```text
//! server  ◄──── explicit sync ────►  MobileBiometricSdk
//!                                        │
//! scanner ───── identify ────────────────┤
//!                                        ├── private gallery.db
//!                                        └── in-memory matcher
//! ```
//!
//! [`MobileBiometricSdk::identify`](crate::kotlin::MobileBiometricSdk::identify)
//! uses only the in-memory matcher. It never waits for SQL or the network. A
//! writer also commits enrollment locally before returning, so a submission
//! survives network loss and process restart.
//!
//! # Synchronization
//!
//! Android schedules
//! [`MobileBiometricSdk::sync`](crate::kotlin::MobileBiometricSdk::sync); the SDK
//! does not run a background scheduler. A sync pushes local enrollment, pulls
//! the canonical membership, templates, and decisions, validates the complete
//! result, builds a replacement matcher, and publishes it atomically. A network
//! or validation failure leaves the previous verified matcher available for
//! identification.
//!
//! New enrollment remains provisional on its originating writer until the
//! server accepts or rejects it. Accepted data becomes canonical and reaches
//! readers on later syncs; rejected data is removed from the writer's matcher.
//!
//! # Lifecycle and threading
//!
//! Open one instance for the assigned gallery and retain it while that
//! assignment is active. A second live instance cannot use the same storage
//! root because the SDK holds an exclusive filesystem lease. First use requires
//! connectivity; later opens may use a verified local gallery offline.
//!
//! `open`, `sync`, token rotation, and enrollment perform blocking work.
//! Fingerprint extraction in `identify` is CPU work. Call all of them away from
//! Android's main thread.
//!
//! Rust owns the database, template artifacts, and matcher. A separate scanner
//! vendor SDK must handle the hardware and give Kotlin raw `400x500`, 8-bit
//! grayscale captures to pass here; Rust does not persist those captures.
//! Generated Kotlin is produced by the mobile build from this annotated API;
//! the binding data types are kept separately in the private `types` module.

use std::sync::Arc;

use crate::sdk::{
    AttendanceBiometricSdk, AttendanceConfig, AttendanceProvisioning, EnrollmentConfig,
};

mod types;

pub use types::{
    MobileDuplicateMatch, MobileEnrollmentAttempt, MobileEnrollmentBatch,
    MobileEnrollmentBatchAuthorization, MobileEnrollmentBatchStatus, MobileEnrollmentReadiness,
    MobileEnrollmentRejection, MobileEnrollmentReport, MobileEnrollmentResult,
    MobileGallerySummary, MobileIdentifyOutcome, MobileRetryReason, MobileSdkError,
    MobileSubjectEnrollmentAuthorization, MobileSyncReport, MobileSyncState,
};

/// Provisioned fixed-population gallery SDK entry point exported through UniFFI.
///
/// The object owns the Rust attendance SDK and therefore the local replica,
/// synchronization client, enrollment state, and currently published matcher.
/// All methods preserve Rust's stable domain behavior while accepting and
/// returning types that UniFFI can represent consistently in Kotlin.
#[derive(Debug, uniffi::Object)]
pub struct MobileBiometricSdk {
    core: AttendanceBiometricSdk,
}

#[uniffi::export]
impl MobileBiometricSdk {
    /// Open or bootstrap the SDK-owned gallery.
    ///
    /// `storage_root` must be a private writable directory dedicated to this
    /// provisioned gallery. `device_instance_id`, `sync_url`, and `auth_token` are issued
    /// together by the server. Supplying `enrollment_min_quality` overrides the
    /// Rust default for captures enrolled by this instance.
    ///
    /// First use requires connectivity. Later opens can use a previously
    /// verified replica when the server is temporarily unreachable. This call
    /// is blocking and must not run on Android's main thread.
    #[uniffi::constructor]
    pub fn open(
        storage_root: String,
        device_instance_id: String,
        sync_url: String,
        auth_token: String,
        enrollment_min_quality: Option<u8>,
    ) -> Result<Arc<Self>, MobileSdkError> {
        let provisioning = AttendanceProvisioning::new(device_instance_id, sync_url, auth_token);
        let mut config = AttendanceConfig::new(storage_root, provisioning);
        if let Some(min_quality) = enrollment_min_quality {
            config = config
                .with_enrollment_config(EnrollmentConfig::default().with_min_quality(min_quality));
        }
        Ok(Arc::new(Self {
            core: AttendanceBiometricSdk::open(config)?,
        }))
    }

    /// Return the latest synchronization state without performing network I/O.
    ///
    /// This is suitable for deciding whether matching can continue after a
    /// failed synchronization. `Offline` still represents a usable, previously
    /// verified local matcher.
    pub fn sync_state(&self) -> Result<MobileSyncState, MobileSdkError> {
        Ok(self.core.sync_state()?.into())
    }

    /// Push pending local WAL and pull canonical gallery changes.
    ///
    /// A successful call validates synchronized rows, builds a replacement
    /// matcher, and publishes it atomically. A failed replacement never
    /// partially mutates the matcher already serving identification requests.
    /// This call is blocking and performs network and database work.
    pub fn sync(&self) -> Result<MobileSyncReport, MobileSdkError> {
        Ok(self.core.sync()?.into())
    }

    /// Replace the bearer credential and verify it with an immediate sync.
    ///
    /// The device identity and synchronization URL remain unchanged. If
    /// verification fails, Rust restores the previous credential and retains
    /// the last published matcher.
    pub fn rotate_auth_token(
        &self,
        auth_token: String,
    ) -> Result<MobileSyncReport, MobileSdkError> {
        Ok(self.core.rotate_auth_token(auth_token)?.into())
    }

    /// Return matcher counts and the canonical gallery revision.
    ///
    /// This is an in-memory snapshot and performs no network or SQL operation.
    pub fn gallery_summary(&self) -> MobileGallerySummary {
        self.core.gallery_stats().into()
    }

    /// Return local enrollment submissions awaiting a server decision.
    ///
    /// Applications can use this count when deciding whether a writer device
    /// may be retired or whether synchronization should be scheduled urgently.
    pub fn pending_enrollment_count(&self) -> Result<u64, MobileSdkError> {
        Ok(self.core.pending_enrollment_count()? as u64)
    }

    /// Identify one subject against the current in-memory gallery matcher.
    ///
    /// `raw` must contain exactly one `400x500` grayscale capture. A non-match
    /// is represented as a typed retry outcome rather than an exception.
    /// Exceptions are reserved for malformed input or SDK failures.
    pub fn identify(&self, raw: Vec<u8>) -> Result<MobileIdentifyOutcome, MobileSdkError> {
        Ok(self.core.identify_raw_bytes(&raw)?.into())
    }

    /// Check writer and synchronized subject readiness before requesting capture.
    pub fn enrollment_readiness(
        &self,
        subject_id: String,
    ) -> Result<MobileEnrollmentReadiness, MobileSdkError> {
        Ok(self.core.enrollment_readiness(&subject_id)?.into())
    }

    /// Start the only active, online-authorized group organizer on this writer.
    ///
    /// The batch is persisted before return and can be recovered with
    /// [`Self::active_group_enrollment`] after process restart.
    pub fn start_group_enrollment(
        &self,
        authorization: MobileEnrollmentBatchAuthorization,
    ) -> Result<MobileEnrollmentBatch, MobileSdkError> {
        self.core
            .start_enrollment_batch(authorization.into())?
            .try_into()
    }

    /// Return the active resumable group-enrollment batch, if one exists.
    pub fn active_group_enrollment(&self) -> Result<Option<MobileEnrollmentBatch>, MobileSdkError> {
        self.core
            .active_enrollment_batch()?
            .map(TryInto::try_into)
            .transpose()
    }

    /// Mark the active group-enrollment batch as successfully completed.
    ///
    /// Finishing a batch does not remove its durable enrollment submissions.
    pub fn finish_group_enrollment(
        &self,
        batch_id: String,
    ) -> Result<MobileEnrollmentBatch, MobileSdkError> {
        self.core.close_enrollment_batch(&batch_id)?.try_into()
    }

    /// Cancel the active group-enrollment batch without deleting submissions.
    pub fn cancel_group_enrollment(
        &self,
        batch_id: String,
    ) -> Result<MobileEnrollmentBatch, MobileSdkError> {
        self.core.cancel_enrollment_batch(&batch_id)?.try_into()
    }

    /// Extract and durably enroll captures for one authorized gallery subject.
    ///
    /// `captures` contains one or more raw `400x500` grayscale images.
    /// The subject-specific authorization is issued online by the application
    /// server. Once capture begins, the local transaction can preserve the
    /// submission through a connection loss or process restart.
    pub fn enroll_subject(
        &self,
        authorization: MobileSubjectEnrollmentAuthorization,
        captures: Vec<Vec<u8>>,
    ) -> Result<MobileEnrollmentResult, MobileSdkError> {
        Ok(self
            .core
            .enroll_subject(authorization.into(), captures)?
            .into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn provisioning_error_does_not_attempt_network_bootstrap() {
        let error = MobileBiometricSdk::open(
            std::env::temp_dir().to_string_lossy().into_owned(),
            "device-1".to_owned(),
            "https://sync.example.test".to_owned(),
            String::new(),
            None,
        )
        .unwrap_err();
        assert!(matches!(error, MobileSdkError::InvalidInput { .. }));
    }
}
