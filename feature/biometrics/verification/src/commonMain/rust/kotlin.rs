//! UniFFI boundary for Kotlin/Android integrations.
//!
//! The binding intentionally exposes user ids, capture bytes, opaque delta
//! bytes, and reusable location handles. Template internals and SDK-generated
//! record ids stay on the Rust side. Snapshot import/export is available both
//! as convenient byte arrays and as bounded chunks for Android storage APIs.

use std::error::Error;
use std::fmt::{self, Display, Formatter};
use std::fs::{self, File, OpenOptions};
use std::io::{Cursor, Read, Write};
use std::path::PathBuf;
use std::sync::{Arc, Mutex, MutexGuard, RwLock};

use crate::sdk::{
    BiometricSdk, BundleStats, DeltaApplyStatus, DuplicateEnrollmentMatch, EnrollmentAttempt,
    EnrollmentCloseResult, EnrollmentDeltaResult, EnrollmentRejectionReason, EnrollmentReport,
    EnrollmentSession, EnrollmentSessionSummary, IdentifyResult, IdentifyRetryReason, IndexDelta,
    LocationIndexBundle, SdkConfig, SdkError, SdkErrorCode,
};

const MAX_EXPORT_CHUNK_BYTES: usize = 1024 * 1024;

/// Stable exception categories generated for Kotlin callers.
#[derive(Debug, uniffi::Error)]
pub enum MobileSdkError {
    /// Caller input is invalid.
    InvalidInput {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Encoded bundle, delta, or session data is malformed.
    InvalidFormat {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Encoded data failed an integrity check.
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
    /// The singleton enrollment session already has an owner.
    SessionActive {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// Filesystem or stream I/O failed.
    Io {
        /// Human-readable diagnostic context.
        message: String,
    },
    /// JSON encoding or decoding failed.
    Serialization {
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
            | Self::Serialization { message }
            | Self::ResourceLimit { message } => message,
        }
    }

    fn conflict(message: impl Into<String>) -> Self {
        Self::Conflict {
            message: message.into(),
        }
    }

    fn io(message: impl Into<String>) -> Self {
        Self::Io {
            message: message.into(),
        }
    }

    fn resource_limit(message: impl Into<String>) -> Self {
        Self::ResourceLimit {
            message: message.into(),
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
            SdkErrorCode::Serialization => Self::Serialization { message },
            SdkErrorCode::ResourceLimit => Self::ResourceLimit { message },
        }
    }
}

/// Compact snapshot summary suitable for app state and sync metadata.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileBundleSummary {
    /// Application location identifier.
    pub location_id: String,
    /// Latest applied synchronization sequence.
    pub generation: u64,
    /// Enrolled finger-template count.
    pub records: u64,
    /// Distinct enrolled user count.
    pub users: u64,
}

impl From<BundleStats> for MobileBundleSummary {
    fn from(stats: BundleStats) -> Self {
        Self {
            location_id: stats.location_id,
            generation: stats.generation,
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
    /// The first two users were too close to choose safely.
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

/// Simple clock-in result: one user match or a retry instruction.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum MobileIdentifyOutcome {
    /// A user passed candidate and geometric verification.
    Match {
        /// Application user identifier.
        user_id: String,
        /// Blended candidate and verification score in `0.0..=1.0`.
        score: f32,
        /// Geometric minutiae verification score in `0.0..=1.0`.
        verification_score: f32,
    },
    /// The app should ask for another scan.
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
                user_id: hit.user_id,
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

/// Existing user found by the duplicate-enrollment guard.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileDuplicateMatch {
    /// Existing application user identifier.
    pub user_id: String,
    /// Blended match score.
    pub score: f32,
    /// Geometric verification score.
    pub verification_score: f32,
}

impl From<DuplicateEnrollmentMatch> for MobileDuplicateMatch {
    fn from(value: DuplicateEnrollmentMatch) -> Self {
        Self {
            user_id: value.user_id,
            score: value.score,
            verification_score: value.verification_score,
        }
    }
}

/// Capture-level enrollment rejection returned to the app.
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
    /// User already has the configured maximum number of templates.
    MaxTemplatesForUser {
        /// Configured per-user template maximum.
        max_templates: u64,
    },
    /// The scan belongs to another enrolled user.
    DuplicateOfOtherUser {
        /// Existing enrollment that matched this scan.
        duplicate: MobileDuplicateMatch,
    },
    /// A batch-level failure rolled back this otherwise acceptable capture.
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
            EnrollmentRejectionReason::MaxTemplatesForUser { max_templates } => {
                Self::MaxTemplatesForUser {
                    max_templates: max_templates as u64,
                }
            }
            EnrollmentRejectionReason::DuplicateOfOtherUser { duplicate } => {
                Self::DuplicateOfOtherUser {
                    duplicate: duplicate.into(),
                }
            }
            EnrollmentRejectionReason::NotCommitted { message } => Self::NotCommitted { message },
        }
    }
}

/// Result of one capture submitted to enrollment.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentAttempt {
    /// Application user identifier supplied by the caller.
    pub user_id: String,
    /// Extracted quality when available.
    pub quality: Option<u8>,
    /// Whether the template was committed.
    pub accepted: bool,
    /// Rejection details when not accepted.
    pub rejection: Option<MobileEnrollmentRejection>,
}

impl From<EnrollmentAttempt> for MobileEnrollmentAttempt {
    fn from(attempt: EnrollmentAttempt) -> Self {
        Self {
            user_id: attempt.user_id,
            quality: attempt.quality,
            accepted: attempt.accepted,
            rejection: attempt.rejection.map(Into::into),
        }
    }
}

/// Enrollment operation report without internal template identifiers.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentReport {
    /// Application location identifier.
    pub location_id: String,
    /// Templates committed by this operation or session.
    pub accepted_records: u64,
    /// Distinct users represented by committed templates.
    pub accepted_users: u64,
    /// Rejected or rolled-back captures.
    pub rejected_captures: u64,
    /// Capture-level outcomes.
    pub attempts: Vec<MobileEnrollmentAttempt>,
}

impl From<EnrollmentReport> for MobileEnrollmentReport {
    fn from(report: EnrollmentReport) -> Self {
        Self {
            location_id: report.location_id,
            accepted_records: report.accepted_records as u64,
            accepted_users: report.accepted_users as u64,
            rejected_captures: report.rejected_captures as u64,
            attempts: report.attempts.into_iter().map(Into::into).collect(),
        }
    }
}

/// Lightweight summary of the resumable initial-enrollment draft.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileEnrollmentSessionSummary {
    /// Application location identifier.
    pub location_id: String,
    /// Templates currently committed to the draft.
    pub accepted_records: u64,
    /// Distinct users currently represented in the draft.
    pub accepted_users: u64,
    /// Rejected captures recorded by the draft.
    pub rejected_captures: u64,
}

impl From<EnrollmentSessionSummary> for MobileEnrollmentSessionSummary {
    fn from(summary: EnrollmentSessionSummary) -> Self {
        Self {
            location_id: summary.location_id,
            accepted_records: summary.accepted_records as u64,
            accepted_users: summary.accepted_users as u64,
            rejected_captures: summary.rejected_captures as u64,
        }
    }
}

/// Initial-enrollment close result.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentCloseResult {
    /// Produced snapshot summary.
    pub bundle: MobileBundleSummary,
    /// Final enrollment report.
    pub report: MobileEnrollmentReport,
}

impl From<EnrollmentCloseResult> for MobileEnrollmentCloseResult {
    fn from(result: EnrollmentCloseResult) -> Self {
        Self {
            bundle: result.bundle.stats().into(),
            report: result.report.into(),
        }
    }
}

/// Future-enrollment result and opaque delta payload for cloud sync.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MobileEnrollmentDeltaResult {
    /// Capture-level operation report.
    pub report: MobileEnrollmentReport,
    /// JSON delta bytes when at least one capture was committed.
    pub delta_json: Option<Vec<u8>>,
}

impl TryFrom<EnrollmentDeltaResult> for MobileEnrollmentDeltaResult {
    type Error = MobileSdkError;

    fn try_from(result: EnrollmentDeltaResult) -> Result<Self, Self::Error> {
        Ok(Self {
            report: result.report.into(),
            delta_json: result
                .delta
                .map(|delta| delta.to_json_bytes())
                .transpose()?,
        })
    }
}

/// Result of applying an opaque synchronization delta.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MobileDeltaApplyStatus {
    /// The operation changed the local bundle.
    Applied,
    /// The exact operation was already present.
    AlreadyApplied,
}

impl From<DeltaApplyStatus> for MobileDeltaApplyStatus {
    fn from(status: DeltaApplyStatus) -> Self {
        match status {
            DeltaApplyStatus::Applied => Self::Applied,
            DeltaApplyStatus::AlreadyApplied => Self::AlreadyApplied,
        }
    }
}

/// One bounded chunk read from an exported bundle.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct MobileBundleChunk {
    /// Snapshot bytes for this chunk.
    pub bytes: Vec<u8>,
    /// Whether this chunk completed the stream.
    pub end_of_stream: bool,
}

/// Filesystem-backed SDK entry point for Kotlin/Android.
#[derive(Debug, uniffi::Object)]
pub struct MobileBiometricSdk {
    core: BiometricSdk,
}

#[uniffi::export]
impl MobileBiometricSdk {
    /// Open the SDK in an app-owned writable directory.
    #[uniffi::constructor]
    pub fn open(
        storage_root: String,
        enrollment_min_quality: Option<u8>,
    ) -> Result<Arc<Self>, MobileSdkError> {
        let mut config = SdkConfig::new(storage_root);
        if let Some(min_quality) = enrollment_min_quality {
            config = config.with_enrollment_min_quality(min_quality);
        }
        Ok(Arc::new(Self {
            core: BiometricSdk::open(config)?,
        }))
    }

    /// Whether an initial-enrollment draft can be resumed.
    pub fn has_active_enrollment_session(&self) -> Result<bool, MobileSdkError> {
        Ok(self.core.has_active_enrollment_session()?)
    }

    /// Start the singleton initial-enrollment session.
    pub fn start_enrollment_session(
        &self,
        location_id: String,
    ) -> Result<Arc<MobileEnrollmentSession>, MobileSdkError> {
        let session = self.core.start_enrollment_session(location_id)?;
        Ok(Arc::new(MobileEnrollmentSession::new(
            self.core.clone(),
            session,
        )))
    }

    /// Resume the persisted singleton initial-enrollment session.
    pub fn resume_enrollment_session(
        &self,
    ) -> Result<Arc<MobileEnrollmentSession>, MobileSdkError> {
        let session = self.core.resume_enrollment_session()?;
        Ok(Arc::new(MobileEnrollmentSession::new(
            self.core.clone(),
            session,
        )))
    }

    /// Discard a persisted draft when no session handle owns it.
    pub fn discard_enrollment_session(&self) -> Result<(), MobileSdkError> {
        Ok(self.core.discard_enrollment_session()?)
    }

    /// Open one reusable in-memory matcher for fast clock-ins.
    pub fn open_location(
        &self,
        location_id: String,
    ) -> Result<Arc<MobileLocationMatcher>, MobileSdkError> {
        let bundle = self.core.load_bundle(&location_id)?;
        Ok(Arc::new(MobileLocationMatcher {
            sdk: self.core.clone(),
            location_id,
            bundle: RwLock::new(bundle),
        }))
    }

    /// Replace one user's templates and return an opaque sync delta.
    pub fn enroll_user(
        &self,
        location_id: String,
        user_id: String,
        captures: Vec<Vec<u8>>,
    ) -> Result<MobileEnrollmentDeltaResult, MobileSdkError> {
        self.core
            .enroll_user(&location_id, user_id, captures)?
            .try_into()
    }

    /// Remove a user and return the opaque sync delta.
    pub fn remove_user(
        &self,
        location_id: String,
        user_id: String,
    ) -> Result<Vec<u8>, MobileSdkError> {
        Ok(self
            .core
            .remove_user(&location_id, &user_id)?
            .to_json_bytes()?)
    }

    /// Apply an opaque delta downloaded for this location.
    pub fn apply_delta(
        &self,
        location_id: String,
        delta_json: Vec<u8>,
    ) -> Result<MobileDeltaApplyStatus, MobileSdkError> {
        let delta = IndexDelta::from_json_bytes(&delta_json)?;
        Ok(self.core.apply_delta(&location_id, &delta)?.into())
    }

    /// Import a complete snapshot byte array.
    pub fn import_bundle_bytes(
        &self,
        location_id: String,
        bundle: Vec<u8>,
    ) -> Result<MobileBundleSummary, MobileSdkError> {
        self.core.import_bundle(&location_id, Cursor::new(bundle))?;
        Ok(self.core.load_bundle(&location_id)?.stats().into())
    }

    /// Export a complete snapshot byte array.
    pub fn export_bundle_bytes(&self, location_id: String) -> Result<Vec<u8>, MobileSdkError> {
        let mut bytes = Vec::new();
        self.core.export_bundle(&location_id, &mut bytes)?;
        Ok(bytes)
    }

    /// Begin a disk-backed chunked snapshot import.
    pub fn begin_bundle_import(
        &self,
        location_id: String,
    ) -> Result<Arc<MobileBundleImport>, MobileSdkError> {
        Ok(Arc::new(MobileBundleImport::new(
            self.core.clone(),
            location_id,
        )?))
    }

    /// Begin a bounded chunked snapshot export.
    pub fn begin_bundle_export(
        &self,
        location_id: String,
    ) -> Result<Arc<MobileBundleExport>, MobileSdkError> {
        Ok(Arc::new(MobileBundleExport::new(
            self.core.clone(),
            location_id,
        )?))
    }
}

/// Exclusive Kotlin handle to the resumable initial-enrollment draft.
#[derive(Debug, uniffi::Object)]
pub struct MobileEnrollmentSession {
    sdk: BiometricSdk,
    session: Mutex<Option<EnrollmentSession>>,
}

impl MobileEnrollmentSession {
    fn new(sdk: BiometricSdk, session: EnrollmentSession) -> Self {
        Self {
            sdk,
            session: Mutex::new(Some(session)),
        }
    }

    fn active_session(&self) -> Result<MutexGuard<'_, Option<EnrollmentSession>>, MobileSdkError> {
        self.session
            .lock()
            .map_err(|_| MobileSdkError::conflict("enrollment session lock is poisoned"))
    }
}

#[uniffi::export]
impl MobileEnrollmentSession {
    /// Add one user's raw capture and durably record the outcome.
    pub fn add_capture(
        &self,
        user_id: String,
        raw: Vec<u8>,
    ) -> Result<MobileEnrollmentAttempt, MobileSdkError> {
        let mut guard = self.active_session()?;
        let session = guard
            .as_mut()
            .ok_or_else(|| MobileSdkError::conflict("enrollment session is already closed"))?;
        Ok(session.add_capture(user_id, &raw)?.into())
    }

    /// Remove one user's draft templates before closing the session.
    pub fn remove_user(&self, user_id: String) -> Result<(), MobileSdkError> {
        let mut guard = self.active_session()?;
        let session = guard
            .as_mut()
            .ok_or_else(|| MobileSdkError::conflict("enrollment session is already closed"))?;
        Ok(session.remove_user(&user_id)?)
    }

    /// Return current persisted draft counts.
    pub fn summary(&self) -> Result<MobileEnrollmentSessionSummary, MobileSdkError> {
        let guard = self.active_session()?;
        let session = guard
            .as_ref()
            .ok_or_else(|| MobileSdkError::conflict("enrollment session is already closed"))?;
        Ok(session.summary().into())
    }

    /// Finish the draft and publish its initial location bundle.
    ///
    /// This is named `finish` because generated UniFFI objects already use
    /// `close` to release their native handle through `AutoCloseable`.
    pub fn finish(&self) -> Result<MobileEnrollmentCloseResult, MobileSdkError> {
        let session = self
            .active_session()?
            .take()
            .ok_or_else(|| MobileSdkError::conflict("enrollment session is already closed"))?;
        Ok(session.close()?.into())
    }

    /// Drop this handle and delete its persisted draft.
    pub fn discard(&self) -> Result<(), MobileSdkError> {
        drop(self.active_session()?.take());
        Ok(self.sdk.discard_enrollment_session()?)
    }
}

/// Reusable in-memory matcher for one location.
#[derive(Debug, uniffi::Object)]
pub struct MobileLocationMatcher {
    sdk: BiometricSdk,
    location_id: String,
    bundle: RwLock<LocationIndexBundle>,
}

#[uniffi::export]
impl MobileLocationMatcher {
    /// Identify one raw capture without reopening the on-disk bundle.
    pub fn identify(&self, raw: Vec<u8>) -> Result<MobileIdentifyOutcome, MobileSdkError> {
        let bundle = self
            .bundle
            .read()
            .map_err(|_| MobileSdkError::conflict("location matcher lock is poisoned"))?;
        Ok(bundle.identify_raw_bytes(&raw)?.into())
    }

    /// Reload this handle after enrollment, removal, or delta application.
    pub fn reload(&self) -> Result<MobileBundleSummary, MobileSdkError> {
        let replacement = self.sdk.load_bundle(&self.location_id)?;
        let summary = replacement.stats().into();
        *self
            .bundle
            .write()
            .map_err(|_| MobileSdkError::conflict("location matcher lock is poisoned"))? =
            replacement;
        Ok(summary)
    }

    /// Return current in-memory snapshot counts.
    pub fn summary(&self) -> Result<MobileBundleSummary, MobileSdkError> {
        Ok(self
            .bundle
            .read()
            .map_err(|_| MobileSdkError::conflict("location matcher lock is poisoned"))?
            .stats()
            .into())
    }
}

#[derive(Debug)]
struct BundleImportState {
    path: PathBuf,
    file: File,
    written: usize,
}

/// Disk-backed chunk receiver for a downloaded location snapshot.
#[derive(Debug, uniffi::Object)]
pub struct MobileBundleImport {
    sdk: BiometricSdk,
    location_id: String,
    state: Mutex<Option<BundleImportState>>,
}

impl MobileBundleImport {
    fn new(sdk: BiometricSdk, location_id: String) -> Result<Self, MobileSdkError> {
        BiometricSdk::validate_location_id(&location_id)?;
        let path = sdk.storage_root().join("tmp").join(format!(
            "bundle-import-{}.tmp",
            uuid::Uuid::new_v4().simple()
        ));
        let file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&path)
            .map_err(|error| {
                MobileSdkError::io(format!("create bundle import {}: {error}", path.display()))
            })?;
        Ok(Self {
            sdk,
            location_id,
            state: Mutex::new(Some(BundleImportState {
                path,
                file,
                written: 0,
            })),
        })
    }

    fn state(&self) -> Result<MutexGuard<'_, Option<BundleImportState>>, MobileSdkError> {
        self.state
            .lock()
            .map_err(|_| MobileSdkError::conflict("bundle import lock is poisoned"))
    }
}

impl Drop for MobileBundleImport {
    fn drop(&mut self) {
        if let Ok(state) = self.state.get_mut()
            && let Some(state) = state.take()
        {
            drop(state.file);
            let _ = fs::remove_file(state.path);
        }
    }
}

#[uniffi::export]
impl MobileBundleImport {
    /// Append one downloaded chunk without accumulating a second full bundle.
    pub fn append(&self, bytes: Vec<u8>) -> Result<(), MobileSdkError> {
        let mut guard = self.state()?;
        let state = guard
            .as_mut()
            .ok_or_else(|| MobileSdkError::conflict("bundle import is already finished"))?;
        let next = state
            .written
            .checked_add(bytes.len())
            .ok_or_else(|| MobileSdkError::resource_limit("bundle import size overflow"))?;
        if next > self.sdk.limits().max_bundle_bytes {
            return Err(MobileSdkError::resource_limit(
                "bundle import exceeds configured byte limit",
            ));
        }
        state.file.write_all(&bytes).map_err(|error| {
            MobileSdkError::io(format!(
                "write bundle import {}: {error}",
                state.path.display()
            ))
        })?;
        state.written = next;
        Ok(())
    }

    /// Validate, atomically install, and close the imported snapshot.
    pub fn finish(&self) -> Result<MobileBundleSummary, MobileSdkError> {
        let mut state = self
            .state()?
            .take()
            .ok_or_else(|| MobileSdkError::conflict("bundle import is already finished"))?;
        state.file.flush().map_err(|error| {
            MobileSdkError::io(format!(
                "flush bundle import {}: {error}",
                state.path.display()
            ))
        })?;
        state.file.sync_all().map_err(|error| {
            MobileSdkError::io(format!(
                "sync bundle import {}: {error}",
                state.path.display()
            ))
        })?;
        drop(state.file);
        /*
        The temporary file keeps downloaded chunks outside the Kotlin and Rust
        heaps. Installation reopens it as a stream, validates the embedded
        location and complete bundle, then atomically writes the canonical SDK
        path. The temporary is removed on either success or failure.
        */
        let result = (|| {
            let file = File::open(&state.path).map_err(|error| {
                MobileSdkError::io(format!(
                    "open bundle import {}: {error}",
                    state.path.display()
                ))
            })?;
            self.sdk.import_bundle(&self.location_id, file)?;
            Ok(self.sdk.load_bundle(&self.location_id)?.stats().into())
        })();
        let _ = fs::remove_file(state.path);
        result
    }

    /// Cancel the import and delete its temporary file.
    pub fn cancel(&self) -> Result<(), MobileSdkError> {
        if let Some(state) = self.state()?.take() {
            drop(state.file);
            if let Err(error) = fs::remove_file(&state.path)
                && error.kind() != std::io::ErrorKind::NotFound
            {
                return Err(MobileSdkError::io(format!(
                    "remove bundle import {}: {error}",
                    state.path.display()
                )));
            }
        }
        Ok(())
    }
}

#[derive(Debug)]
struct BundleExportState {
    file: File,
    remaining: u64,
}

/// Bounded chunk reader for an installed location snapshot.
#[derive(Debug, uniffi::Object)]
pub struct MobileBundleExport {
    state: Mutex<BundleExportState>,
}

impl MobileBundleExport {
    fn new(sdk: BiometricSdk, location_id: String) -> Result<Self, MobileSdkError> {
        sdk.load_bundle(&location_id)?;
        let path = sdk.bundle_path(&location_id);
        let file = File::open(&path).map_err(|error| {
            MobileSdkError::io(format!("open bundle export {}: {error}", path.display()))
        })?;
        let remaining = file
            .metadata()
            .map_err(|error| {
                MobileSdkError::io(format!("read bundle export {}: {error}", path.display()))
            })?
            .len();
        Ok(Self {
            state: Mutex::new(BundleExportState { file, remaining }),
        })
    }
}

#[uniffi::export]
impl MobileBundleExport {
    /// Read at most one MiB from the snapshot stream.
    pub fn read_chunk(&self, max_bytes: u32) -> Result<MobileBundleChunk, MobileSdkError> {
        if max_bytes == 0 {
            return Err(MobileSdkError::InvalidInput {
                message: "max_bytes must be greater than zero".to_owned(),
            });
        }
        let mut state = self
            .state
            .lock()
            .map_err(|_| MobileSdkError::conflict("bundle export lock is poisoned"))?;
        let requested = usize::try_from(max_bytes)
            .unwrap_or(MAX_EXPORT_CHUNK_BYTES)
            .min(MAX_EXPORT_CHUNK_BYTES)
            .min(state.remaining as usize);
        let mut bytes = vec![0; requested];
        state
            .file
            .read_exact(&mut bytes)
            .map_err(|error| MobileSdkError::io(format!("read bundle export stream: {error}")))?;
        state.remaining = state.remaining.saturating_sub(bytes.len() as u64);
        Ok(MobileBundleChunk {
            bytes,
            end_of_stream: state.remaining == 0,
        })
    }

    /// Number of snapshot bytes not yet read.
    pub fn remaining_bytes(&self) -> Result<u64, MobileSdkError> {
        Ok(self
            .state
            .lock()
            .map_err(|_| MobileSdkError::conflict("bundle export lock is poisoned"))?
            .remaining)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::sdk::{LocationIndexBundle, TemplateStore};

    #[test]
    fn byte_and_chunk_bundle_boundaries_round_trip() {
        let source_root = temp_root("source");
        let target_root = temp_root("target");
        let source = MobileBiometricSdk::open(source_root, None).unwrap();
        let target = MobileBiometricSdk::open(target_root, None).unwrap();
        source
            .core
            .save_bundle(&LocationIndexBundle::build("school", TemplateStore::new()).unwrap())
            .unwrap();

        let exported = source.export_bundle_bytes("school".to_owned()).unwrap();
        let importer = target.begin_bundle_import("school".to_owned()).unwrap();
        for chunk in exported.chunks(17) {
            importer.append(chunk.to_vec()).unwrap();
        }
        assert_eq!(importer.finish().unwrap().location_id, "school");

        let exporter = target.begin_bundle_export("school".to_owned()).unwrap();
        let mut streamed = Vec::new();
        loop {
            let chunk = exporter.read_chunk(13).unwrap();
            streamed.extend(chunk.bytes);
            if chunk.end_of_stream {
                break;
            }
        }
        assert_eq!(streamed, exported);
    }

    fn temp_root(label: &str) -> String {
        std::env::temp_dir()
            .join(format!(
                "biometric-sdk-kotlin-{label}-{}",
                uuid::Uuid::new_v4().simple()
            ))
            .to_string_lossy()
            .into_owned()
    }
}
