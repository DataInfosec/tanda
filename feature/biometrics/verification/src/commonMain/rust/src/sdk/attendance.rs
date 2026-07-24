//! SDK-owned libSQL gallery persistence and synchronization.
//!
//! [`AttendanceBiometricSdk`] is the high-level attendance API. It owns the replica
//! file, serializes every SQL mutation with libSQL synchronization, and keeps a
//! validated [`GalleryIndex`] published for network-independent matching. The
//! Android application supplies provisioning and biometric commands; it never
//! opens the gallery database or interprets synchronized rows.

use std::fmt::{self, Debug, Formatter};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, MutexGuard};

use arc_swap::ArcSwap;
use chrono::{SecondsFormat, Utc};
use fs2::FileExt;
use libsql::{Builder, Connection, Database, TransactionBehavior, params};
use tokio::runtime::Runtime;
use uuid::Uuid;

use super::artifact::{
    TemplateArtifactRef, decode_subject_template_artifact, template_payload_checksum,
};
use super::enrollment::{
    DuplicateEnrollmentMatch, EnrollmentAttempt, EnrollmentConfig, EnrollmentRejectionReason,
    EnrollmentReport,
};
use super::error::{SdkError, SdkResult};
use super::extractor::{ExtractedTemplate, ExtractorConfig, extract_raw_bytes};
use super::gallery::{GalleryIndex, GalleryStats};
use super::index::{BiometricIndex, IdentifyConfig, IdentifyResult};
use super::limits::SdkLimits;
use super::storage::validate_identifier;
use super::template::{DEFAULT_EXTRACTOR_PROFILE, TEMPLATE_FORMAT_VERSION, TemplateStore};

const GALLERY_SCHEMA: &str = "tanda-gallery";
const GALLERY_SCHEMA_VERSION: &str = "3";
const REPLICA_FILE: &str = "gallery.db";
const REPLICA_LOCK_FILE: &str = "gallery.lock";

/// Remote endpoint and credential issued for one device.
#[derive(Clone)]
pub struct AttendanceProvisioning {
    /// Stable physical device-instance identifier bound to the bearer credential.
    pub device_instance_id: String,
    /// Base endpoint implementing the official libSQL sync protocol.
    pub sync_url: String,
    /// Bearer credential used only by the libSQL client.
    pub auth_token: String,
}

impl AttendanceProvisioning {
    /// Construct device provisioning.
    pub fn new(
        device_instance_id: impl Into<String>,
        sync_url: impl Into<String>,
        auth_token: impl Into<String>,
    ) -> Self {
        Self {
            device_instance_id: device_instance_id.into(),
            sync_url: sync_url.into(),
            auth_token: auth_token.into(),
        }
    }

    fn validate(&self) -> SdkResult<()> {
        validate_identifier("device_instance_id", &self.device_instance_id)?;
        if self.sync_url.trim().is_empty() {
            return Err(SdkError::invalid_input("sync_url is required"));
        }
        if !(self.sync_url.starts_with("https://") || self.sync_url.starts_with("http://")) {
            return Err(SdkError::invalid_input("sync_url must use http or https"));
        }
        if self.auth_token.trim().is_empty() {
            return Err(SdkError::invalid_input("auth_token is required"));
        }
        Ok(())
    }
}

impl Debug for AttendanceProvisioning {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("AttendanceProvisioning")
            .field("device_instance_id", &self.device_instance_id)
            .field("sync_url", &self.sync_url)
            .field("auth_token", &"[redacted]")
            .finish()
    }
}

/// Initialization settings for the attendance SDK.
#[derive(Debug, Clone)]
pub struct AttendanceConfig {
    /// SDK-owned writable directory.
    pub storage_root: PathBuf,
    /// Device provisioning for synchronized mode.
    pub provisioning: AttendanceProvisioning,
    /// Enrollment acceptance policy.
    pub enrollment: EnrollmentConfig,
    /// Fingerprint extraction profile.
    pub extractor: ExtractorConfig,
    /// Identity acceptance policy.
    pub identify: IdentifyConfig,
    /// Allocation and record limits.
    pub limits: SdkLimits,
}

impl AttendanceConfig {
    /// Construct synchronized attendance settings with current biometric defaults.
    pub fn new(storage_root: impl Into<PathBuf>, provisioning: AttendanceProvisioning) -> Self {
        Self {
            storage_root: storage_root.into(),
            provisioning,
            enrollment: EnrollmentConfig::default(),
            extractor: ExtractorConfig::default(),
            identify: IdentifyConfig::default(),
            limits: SdkLimits::default(),
        }
    }

    /// Replace enrollment acceptance policy.
    pub fn with_enrollment_config(mut self, enrollment: EnrollmentConfig) -> Self {
        self.enrollment = enrollment;
        self
    }

    /// Replace the query extractor profile.
    pub fn with_extractor_config(mut self, extractor: ExtractorConfig) -> Self {
        self.extractor = extractor;
        self
    }

    /// Replace identity acceptance policy.
    pub fn with_identify_config(mut self, identify: IdentifyConfig) -> Self {
        self.identify = identify;
        self
    }

    /// Replace allocation and record limits.
    pub fn with_limits(mut self, limits: SdkLimits) -> Self {
        self.limits = limits;
        self
    }
}

/// Observable synchronization state.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AttendanceSyncState {
    /// The replica is usable and its latest sync completed.
    Ready,
    /// Existing local state is usable but the latest network sync failed.
    Offline,
    /// The server revoked this device's gallery write authority.
    WriterRevoked,
    /// Synchronized rows failed validation; the previous matcher remains live.
    Quarantined,
}

/// Outcome of one synchronization attempt.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AttendanceSyncReport {
    /// State after the attempt.
    pub state: AttendanceSyncState,
    /// Number of physical 4 KiB frames transferred by libSQL.
    pub frames_synced: usize,
    /// Current application-level gallery revision.
    pub gallery_revision: u64,
    /// Current indexed subject count, including local provisional enrollment.
    pub indexed_subjects: usize,
    /// Enrollment submissions from this device awaiting a server decision.
    pub pending_enrollments: usize,
}

/// Persisted group-enrollment batch.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EnrollmentBatch {
    /// UUIDv7 batch identifier.
    pub id: String,
    /// Device that owns this resumable batch.
    pub device_instance_id: String,
    /// Canonical administrator who opened the batch.
    pub performed_by: String,
    /// Opaque server-issued batch authorization identifier.
    pub authorization_id: String,
    /// UTC timestamp after which no new subject capture may join this batch.
    pub authorization_expires_at: String,
    /// `active`, `closed`, or `cancelled`.
    pub status: String,
    /// UTC timestamp at which the batch started.
    pub started_at: String,
    /// UTC terminal timestamp when the batch is no longer active.
    pub closed_at: Option<String>,
}

/// Enrollment result after local transaction commit.
#[derive(Debug, Clone, PartialEq)]
pub struct AttendanceEnrollmentResult {
    /// UUIDv7 submission identifier when at least one capture was accepted.
    pub submission_id: Option<String>,
    /// Group batch containing the submission, when supplied.
    pub batch_id: Option<String>,
    /// Capture-level extraction and acceptance outcomes.
    pub report: EnrollmentReport,
}

/// Server-issued authorization for opening a group enrollment organizer.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EnrollmentBatchAuthorization {
    /// Opaque server authorization identifier.
    pub authorization_id: String,
    /// Canonical administrator identity.
    pub performed_by: String,
    /// Physical writer instance authorized by the server.
    pub device_instance_id: String,
    /// Gallery authorized by the server.
    pub gallery_id: String,
    /// UTC RFC3339 expiry.
    pub authorization_expires_at: String,
}

/// Server-issued authorization for one subject enrollment operation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SubjectEnrollmentAuthorization {
    /// Opaque, idempotent operation identifier.
    pub enrollment_operation_id: String,
    /// Canonical administrator identity.
    pub performed_by: String,
    /// Physical writer instance authorized by the server.
    pub device_instance_id: String,
    /// Gallery authorized by the server.
    pub gallery_id: String,
    /// Canonical subject authorized for capture.
    pub subject_id: String,
    /// Optional group organizer to which the operation belongs.
    pub batch_id: Option<String>,
    /// UTC RFC3339 expiry.
    pub authorization_expires_at: String,
}

/// Stable local readiness state checked before requesting a biometric capture.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EnrollmentReadiness {
    /// Writer state and synchronized subject membership are ready.
    Ready,
    /// The subject has not reached the local gallery yet.
    GallerySyncRequired,
    /// Server synchronization revoked this instance's writer authority.
    WriterRevoked,
    /// Synchronized gallery rows failed validation.
    Quarantined,
}

/// Attendance-grade evidence returned atomically with an accepted fingerprint match.
#[derive(Debug, Clone, PartialEq)]
pub struct IdentificationEvidence {
    /// Canonical matched subject.
    pub subject_id: String,
    /// Finger record that produced the strongest accepted match.
    pub record_id: String,
    /// Gallery searched for this decision.
    pub gallery_id: String,
    /// Immutable gallery revision searched for this decision.
    pub gallery_revision: u64,
    /// Biometric modality used for the decision.
    pub modality: String,
    /// Final blended score.
    pub score: f32,
    /// Geometric verification score.
    pub verification_score: f32,
}

/// Identification result with atomic attendance evidence on success.
#[derive(Debug, Clone, PartialEq)]
pub enum AttendanceIdentifyResult {
    /// Accepted subject match.
    Match(IdentificationEvidence),
    /// Capture should be retried.
    Retry(super::index::IdentifyRetry),
}

/// SDK facade owning one provisioned fixed-population gallery.
#[derive(Clone)]
pub struct AttendanceBiometricSdk {
    inner: Arc<AttendanceInner>,
}

impl Debug for AttendanceBiometricSdk {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("AttendanceBiometricSdk")
            .field("storage_root", &self.inner.storage_root)
            .field("device_instance_id", &self.inner.device_instance_id)
            .field("gallery", &self.inner.gallery.load().stats())
            .finish_non_exhaustive()
    }
}

struct AttendanceInner {
    storage_root: PathBuf,
    device_instance_id: String,
    runtime: Runtime,
    database: Mutex<DatabaseState>,
    operations: Mutex<()>,
    gallery: ArcSwap<GalleryIndex>,
    enrollment: EnrollmentConfig,
    extractor: ExtractorConfig,
    identify: IdentifyConfig,
    limits: SdkLimits,
    _lock_file: File,
}

struct DatabaseState {
    database: Option<Database>,
    sync_state: AttendanceSyncState,
    remote: Option<RemoteConfiguration>,
}

#[derive(Clone)]
struct RemoteConfiguration {
    sync_url: String,
    auth_token: String,
}

impl DatabaseState {
    fn database(&self) -> SdkResult<&Database> {
        self.database
            .as_ref()
            .ok_or_else(|| SdkError::database("gallery database is being reconfigured"))
    }
}

struct GalleryRows {
    gallery_id: String,
    gallery_revision: u64,
    templates: TemplateStore,
}

impl AttendanceBiometricSdk {
    /// Open or bootstrap one synchronized fixed-population gallery.
    ///
    /// A new replica requires connectivity. An existing verified replica may
    /// open in [`AttendanceSyncState::Offline`] when the initial refresh fails.
    pub fn open(config: AttendanceConfig) -> SdkResult<Self> {
        config.provisioning.validate()?;
        let limits = config.limits.validate()?;
        let enrollment = config.enrollment.validate(limits)?;
        let extractor = config.extractor.validate(limits)?;
        fs::create_dir_all(&config.storage_root).map_err(|error| {
            SdkError::io(
                format!(
                    "create attendance storage {}",
                    config.storage_root.display()
                ),
                error,
            )
        })?;
        let lock_path = config.storage_root.join(REPLICA_LOCK_FILE);
        let mut lock_file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .open(&lock_path)
            .map_err(|error| {
                SdkError::io(
                    format!("open attendance lock {}", lock_path.display()),
                    error,
                )
            })?;
        lock_file
            .try_lock_exclusive()
            .map_err(|_| SdkError::session_active("another SDK instance owns the gallery"))?;
        bind_storage_to_device_instance(&mut lock_file, &config.provisioning.device_instance_id)?;

        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .map_err(|error| SdkError::database(format!("create SDK runtime: {error}")))?;
        let replica_path = config.storage_root.join(REPLICA_FILE);
        let existing_replica = replica_path.exists();
        let (database, sync_state) = runtime.block_on(open_synced_database(
            &replica_path,
            &config.provisioning,
            existing_replica,
        ))?;
        let rows = runtime.block_on(load_gallery_rows(
            &database,
            &config.provisioning.device_instance_id,
            extractor,
            limits,
        ))?;
        let gallery = GalleryIndex::build_with_profiles(
            rows.gallery_id,
            rows.gallery_revision,
            rows.templates,
            extractor,
            config.identify,
            limits,
        )?;

        Ok(Self {
            inner: Arc::new(AttendanceInner {
                storage_root: config.storage_root,
                device_instance_id: config.provisioning.device_instance_id,
                runtime,
                database: Mutex::new(DatabaseState {
                    database: Some(database),
                    sync_state,
                    remote: Some(RemoteConfiguration {
                        sync_url: config.provisioning.sync_url,
                        auth_token: config.provisioning.auth_token,
                    }),
                }),
                operations: Mutex::new(()),
                gallery: ArcSwap::from_pointee(gallery),
                enrollment,
                extractor,
                identify: config.identify,
                limits,
                _lock_file: lock_file,
            }),
        })
    }

    /// Open an unsynchronized libSQL gallery for tests and isolated deployments.
    pub fn open_local(
        storage_root: impl Into<PathBuf>,
        gallery_id: impl Into<String>,
        device_instance_id: impl Into<String>,
    ) -> SdkResult<Self> {
        Self::open_local_with_config(
            storage_root.into(),
            gallery_id.into(),
            device_instance_id.into(),
            EnrollmentConfig::default(),
            ExtractorConfig::default(),
            IdentifyConfig::default(),
            SdkLimits::default(),
        )
    }

    fn open_local_with_config(
        storage_root: PathBuf,
        gallery_id: String,
        device_instance_id: String,
        enrollment: EnrollmentConfig,
        extractor: ExtractorConfig,
        identify: IdentifyConfig,
        limits: SdkLimits,
    ) -> SdkResult<Self> {
        validate_identifier("gallery_id", &gallery_id)?;
        validate_identifier("device_instance_id", &device_instance_id)?;
        let limits = limits.validate()?;
        let enrollment = enrollment.validate(limits)?;
        let extractor = extractor.validate(limits)?;
        fs::create_dir_all(&storage_root).map_err(|error| {
            SdkError::io(
                format!("create attendance storage {}", storage_root.display()),
                error,
            )
        })?;
        let lock_path = storage_root.join(REPLICA_LOCK_FILE);
        let mut lock_file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .open(&lock_path)
            .map_err(|error| {
                SdkError::io(
                    format!("open attendance lock {}", lock_path.display()),
                    error,
                )
            })?;
        lock_file
            .try_lock_exclusive()
            .map_err(|_| SdkError::session_active("another SDK instance owns the gallery"))?;
        bind_storage_to_device_instance(&mut lock_file, &device_instance_id)?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .map_err(|error| SdkError::database(format!("create SDK runtime: {error}")))?;
        let database = runtime.block_on(open_local_database(
            &storage_root.join(REPLICA_FILE),
            &gallery_id,
        ))?;
        let rows = runtime.block_on(load_gallery_rows(
            &database,
            &device_instance_id,
            extractor,
            limits,
        ))?;
        let gallery = GalleryIndex::build_with_profiles(
            rows.gallery_id,
            rows.gallery_revision,
            rows.templates,
            extractor,
            identify,
            limits,
        )?;
        Ok(Self {
            inner: Arc::new(AttendanceInner {
                storage_root,
                device_instance_id,
                runtime,
                database: Mutex::new(DatabaseState {
                    database: Some(database),
                    sync_state: AttendanceSyncState::Ready,
                    remote: None,
                }),
                operations: Mutex::new(()),
                gallery: ArcSwap::from_pointee(gallery),
                enrollment,
                extractor,
                identify,
                limits,
                _lock_file: lock_file,
            }),
        })
    }

    /// Return current replica synchronization state.
    pub fn sync_state(&self) -> SdkResult<AttendanceSyncState> {
        Ok(self.database()?.sync_state)
    }

    /// Return current immutable matcher sizing and revision.
    pub fn gallery_stats(&self) -> GalleryStats {
        self.inner.gallery.load().stats()
    }

    /// Match a raw scan without opening the database or waiting for sync.
    pub fn identify_raw_bytes(&self, raw: &[u8]) -> SdkResult<AttendanceIdentifyResult> {
        let gallery = self.inner.gallery.load_full();
        Ok(attach_gallery_evidence(
            &gallery,
            gallery.identify_raw_bytes(raw)?,
        ))
    }

    /// Synchronize local WAL state and publish a rebuilt matcher when needed.
    pub fn sync(&self) -> SdkResult<AttendanceSyncReport> {
        let _operation = self.operation()?;
        let mut state = self.database()?;
        if state.sync_state == AttendanceSyncState::WriterRevoked {
            return Err(SdkError::conflict("gallery writer authority was revoked"));
        }
        if state.remote.is_none() {
            let gallery = match self.rebuild(state.database()?) {
                Ok(gallery) => gallery,
                Err(error) => {
                    state.sync_state = AttendanceSyncState::Quarantined;
                    return Err(error);
                }
            };
            let pending_enrollments = match self.pending_count(state.database()?) {
                Ok(count) => count,
                Err(error) => {
                    state.sync_state = AttendanceSyncState::Quarantined;
                    return Err(error);
                }
            };
            let report = AttendanceSyncReport {
                state: AttendanceSyncState::Ready,
                frames_synced: 0,
                gallery_revision: gallery.gallery_revision(),
                indexed_subjects: gallery.stats().subjects,
                pending_enrollments,
            };
            state.sync_state = AttendanceSyncState::Ready;
            self.inner.gallery.store(Arc::new(gallery));
            return Ok(report);
        }
        let replicated = match self.inner.runtime.block_on(state.database()?.sync()) {
            Ok(replicated) => replicated,
            Err(error) => {
                if is_writer_forbidden(&error.to_string()) {
                    state.sync_state = AttendanceSyncState::WriterRevoked;
                    return Err(SdkError::conflict(
                        "server revoked gallery writer authority",
                    ));
                }
                state.sync_state = AttendanceSyncState::Offline;
                return Err(SdkError::sync(format!("synchronize gallery: {error}")));
            }
        };
        let gallery = match self.rebuild(state.database()?) {
            Ok(gallery) => gallery,
            Err(error) => {
                state.sync_state = AttendanceSyncState::Quarantined;
                return Err(error);
            }
        };
        let pending_enrollments = match self.pending_count(state.database()?) {
            Ok(count) => count,
            Err(error) => {
                state.sync_state = AttendanceSyncState::Quarantined;
                return Err(error);
            }
        };
        state.sync_state = AttendanceSyncState::Ready;
        let report = AttendanceSyncReport {
            state: state.sync_state,
            frames_synced: replicated.frames_synced(),
            gallery_revision: gallery.gallery_revision(),
            indexed_subjects: gallery.stats().subjects,
            pending_enrollments,
        };
        self.inner.gallery.store(Arc::new(gallery));
        Ok(report)
    }

    /// Replace the bearer credential and verify it with an immediate sync.
    ///
    /// The sync URL and device identity remain fixed. A failed rotation restores
    /// the previous credential and leaves the last published matcher available.
    pub fn rotate_auth_token(
        &self,
        auth_token: impl Into<String>,
    ) -> SdkResult<AttendanceSyncReport> {
        let auth_token = auth_token.into();
        if auth_token.trim().is_empty() {
            return Err(SdkError::invalid_input("auth_token is required"));
        }
        let _operation = self.operation()?;
        let mut state = self.database()?;
        let previous = state
            .remote
            .clone()
            .ok_or_else(|| SdkError::invalid_input("local galleries have no auth token"))?;
        let old_database = state
            .database
            .take()
            .ok_or_else(|| SdkError::database("gallery database is unavailable"))?;
        drop(old_database);

        let provisioning = AttendanceProvisioning::new(
            self.inner.device_instance_id.clone(),
            previous.sync_url.clone(),
            auth_token.clone(),
        );
        let path = self.inner.storage_root.join(REPLICA_FILE);
        let candidate = match self
            .inner
            .runtime
            .block_on(build_synced_database(&path, &provisioning))
        {
            Ok(candidate) => candidate,
            Err(error) => {
                self.restore_database(&mut state, previous)?;
                return Err(error);
            }
        };
        let replicated = match self.inner.runtime.block_on(candidate.sync()) {
            Ok(replicated) => replicated,
            Err(error) => {
                drop(candidate);
                self.restore_database(&mut state, previous)?;
                return Err(SdkError::sync(format!(
                    "verify replacement auth token: {error}"
                )));
            }
        };
        let gallery = match self.rebuild(&candidate) {
            Ok(gallery) => gallery,
            Err(error) => {
                state.database = Some(candidate);
                state.remote = Some(RemoteConfiguration {
                    sync_url: provisioning.sync_url,
                    auth_token,
                });
                state.sync_state = AttendanceSyncState::Quarantined;
                return Err(error);
            }
        };
        let pending_enrollments = match self.pending_count(&candidate) {
            Ok(count) => count,
            Err(error) => {
                state.database = Some(candidate);
                state.remote = Some(RemoteConfiguration {
                    sync_url: provisioning.sync_url,
                    auth_token,
                });
                state.sync_state = AttendanceSyncState::Quarantined;
                return Err(error);
            }
        };
        state.database = Some(candidate);
        state.remote = Some(RemoteConfiguration {
            sync_url: provisioning.sync_url,
            auth_token,
        });
        state.sync_state = AttendanceSyncState::Ready;
        let report = AttendanceSyncReport {
            state: state.sync_state,
            frames_synced: replicated.frames_synced(),
            gallery_revision: gallery.gallery_revision(),
            indexed_subjects: gallery.stats().subjects,
            pending_enrollments,
        };
        self.inner.gallery.store(Arc::new(gallery));
        Ok(report)
    }

    /// Return this device's enrollment submissions awaiting server decisions.
    pub fn pending_enrollment_count(&self) -> SdkResult<usize> {
        let state = self.database()?;
        self.pending_count(state.database()?)
    }

    /// Check local writer and subject-membership readiness before capture.
    pub fn enrollment_readiness(&self, subject_id: &str) -> SdkResult<EnrollmentReadiness> {
        validate_identifier("subject_id", subject_id)?;
        let state = self.database()?;
        match state.sync_state {
            AttendanceSyncState::WriterRevoked => Ok(EnrollmentReadiness::WriterRevoked),
            AttendanceSyncState::Quarantined => Ok(EnrollmentReadiness::Quarantined),
            AttendanceSyncState::Ready | AttendanceSyncState::Offline => {
                let exists = self
                    .inner
                    .runtime
                    .block_on(active_gallery_member_exists(state.database()?, subject_id))?;
                Ok(if exists {
                    EnrollmentReadiness::Ready
                } else {
                    EnrollmentReadiness::GallerySyncRequired
                })
            }
        }
    }

    /// Start this writer's only active, online-authorized group organizer.
    pub fn start_enrollment_batch(
        &self,
        authorization: EnrollmentBatchAuthorization,
    ) -> SdkResult<EnrollmentBatch> {
        let _operation = self.operation()?;
        let state = self.database()?;
        ensure_enrollment_state(state.sync_state)?;
        let gallery_id = self.inner.gallery.load().gallery_id().to_owned();
        validate_batch_authorization(&authorization, &self.inner.device_instance_id, &gallery_id)?;
        let batch = EnrollmentBatch {
            id: Uuid::now_v7().to_string(),
            device_instance_id: self.inner.device_instance_id.clone(),
            performed_by: authorization.performed_by,
            authorization_id: authorization.authorization_id,
            authorization_expires_at: authorization.authorization_expires_at,
            status: "active".to_owned(),
            started_at: now_text(),
            closed_at: None,
        };
        self.inner
            .runtime
            .block_on(insert_enrollment_batch(state.database()?, &batch))?;
        Ok(batch)
    }

    /// Return this writer's active group-enrollment batch, when one exists.
    pub fn active_enrollment_batch(&self) -> SdkResult<Option<EnrollmentBatch>> {
        let state = self.database()?;
        self.inner.runtime.block_on(query_active_enrollment_batch(
            state.database()?,
            &self.inner.device_instance_id,
        ))
    }

    /// Close the active group-enrollment batch.
    pub fn close_enrollment_batch(&self, batch_id: &str) -> SdkResult<EnrollmentBatch> {
        validate_identifier("batch_id", batch_id)?;
        let _operation = self.operation()?;
        let state = self.database()?;
        ensure_enrollment_state(state.sync_state)?;
        self.inner.runtime.block_on(close_enrollment_batch(
            state.database()?,
            batch_id,
            &self.inner.device_instance_id,
            "closed",
        ))
    }

    /// Cancel the active group-enrollment batch without deleting submissions.
    pub fn cancel_enrollment_batch(&self, batch_id: &str) -> SdkResult<EnrollmentBatch> {
        validate_identifier("batch_id", batch_id)?;
        let _operation = self.operation()?;
        let state = self.database()?;
        ensure_enrollment_state(state.sync_state)?;
        self.inner.runtime.block_on(close_enrollment_batch(
            state.database()?,
            batch_id,
            &self.inner.device_instance_id,
            "cancelled",
        ))
    }

    /// Extract, validate, and durably queue one authorized subject enrollment.
    pub fn enroll_subject<I, R>(
        &self,
        authorization: SubjectEnrollmentAuthorization,
        captures: I,
    ) -> SdkResult<AttendanceEnrollmentResult>
    where
        I: IntoIterator<Item = R>,
        R: AsRef<[u8]>,
    {
        let _operation = self.operation()?;
        let current = self.inner.gallery.load_full();
        validate_subject_authorization(
            &authorization,
            &self.inner.device_instance_id,
            current.gallery_id(),
        )?;
        {
            let state = self.database()?;
            ensure_enrollment_state(state.sync_state)?;
            self.inner.runtime.block_on(ensure_enrollment_authorized(
                state.database()?,
                &authorization,
                &self.inner.device_instance_id,
            ))?;
        }
        let (artifact, report) = prepare_enrollment(
            current.gallery_id(),
            &authorization.subject_id,
            captures,
            &current.templates(),
            self.inner.enrollment,
            self.inner.extractor,
            self.inner.limits,
        )?;
        let Some(artifact) = artifact else {
            return Ok(AttendanceEnrollmentResult {
                submission_id: None,
                batch_id: authorization.batch_id,
                report,
            });
        };

        let submission_id = Uuid::now_v7().to_string();
        let payload = artifact.to_bytes_with_config(self.inner.extractor, self.inner.limits)?;
        let checksum = template_payload_checksum(&payload);
        let state = self.database()?;
        ensure_enrollment_state(state.sync_state)?;
        self.inner.runtime.block_on(insert_enrollment_submission(
            state.database()?,
            EnrollmentSubmissionRow {
                id: &submission_id,
                batch_id: authorization.batch_id.as_deref(),
                device_instance_id: &self.inner.device_instance_id,
                subject_id: &authorization.subject_id,
                enrollment_operation_id: &authorization.enrollment_operation_id,
                performed_by: &authorization.performed_by,
                authorization_expires_at: &authorization.authorization_expires_at,
                gallery_revision: current.gallery_revision(),
                payload: &payload,
                checksum: &checksum,
            },
        ))?;
        let gallery = self.rebuild(state.database()?)?;
        self.inner.gallery.store(Arc::new(gallery));
        Ok(AttendanceEnrollmentResult {
            submission_id: Some(submission_id),
            batch_id: authorization.batch_id,
            report,
        })
    }

    fn rebuild(&self, database: &Database) -> SdkResult<GalleryIndex> {
        let rows = self.inner.runtime.block_on(load_gallery_rows(
            database,
            &self.inner.device_instance_id,
            self.inner.extractor,
            self.inner.limits,
        ))?;
        GalleryIndex::build_with_profiles(
            rows.gallery_id,
            rows.gallery_revision,
            rows.templates,
            self.inner.extractor,
            self.inner.identify,
            self.inner.limits,
        )
    }

    fn pending_count(&self, database: &Database) -> SdkResult<usize> {
        self.inner.runtime.block_on(query_pending_enrollment_count(
            database,
            &self.inner.device_instance_id,
        ))
    }

    fn restore_database(
        &self,
        state: &mut DatabaseState,
        remote: RemoteConfiguration,
    ) -> SdkResult<()> {
        let provisioning = AttendanceProvisioning::new(
            self.inner.device_instance_id.clone(),
            remote.sync_url.clone(),
            remote.auth_token.clone(),
        );
        let (database, sync_state) = self.inner.runtime.block_on(open_synced_database(
            &self.inner.storage_root.join(REPLICA_FILE),
            &provisioning,
            true,
        ))?;
        state.database = Some(database);
        state.sync_state = sync_state;
        state.remote = Some(remote);
        Ok(())
    }

    fn database(&self) -> SdkResult<MutexGuard<'_, DatabaseState>> {
        self.inner
            .database
            .lock()
            .map_err(|_| SdkError::database("gallery database lock is poisoned"))
    }

    fn operation(&self) -> SdkResult<MutexGuard<'_, ()>> {
        self.inner
            .operations
            .lock()
            .map_err(|_| SdkError::database("gallery operation lock is poisoned"))
    }
}

fn attach_gallery_evidence(
    gallery: &GalleryIndex,
    result: IdentifyResult,
) -> AttendanceIdentifyResult {
    match result {
        IdentifyResult::Match(hit) => AttendanceIdentifyResult::Match(IdentificationEvidence {
            subject_id: hit.user_id,
            record_id: hit.record_id,
            gallery_id: gallery.gallery_id().to_owned(),
            gallery_revision: gallery.gallery_revision(),
            modality: "fingerprint".to_owned(),
            score: hit.score,
            verification_score: hit.verification_score,
        }),
        IdentifyResult::Retry(retry) => AttendanceIdentifyResult::Retry(retry),
    }
}

fn bind_storage_to_device_instance(
    lock_file: &mut File,
    device_instance_id: &str,
) -> SdkResult<()> {
    validate_identifier("device_instance_id", device_instance_id)?;
    let marker_len = lock_file
        .metadata()
        .map_err(|error| SdkError::io("read gallery lock metadata", error))?
        .len();
    if marker_len == 0 {
        lock_file
            .seek(SeekFrom::Start(0))
            .map_err(|error| SdkError::io("seek gallery instance marker", error))?;
        lock_file
            .write_all(device_instance_id.as_bytes())
            .map_err(|error| SdkError::io("write gallery instance marker", error))?;
        lock_file
            .sync_data()
            .map_err(|error| SdkError::io("persist gallery instance marker", error))?;
        return Ok(());
    }
    if marker_len > 256 {
        return Err(SdkError::integrity(
            "gallery instance marker exceeds the identifier limit",
        ));
    }
    lock_file
        .seek(SeekFrom::Start(0))
        .map_err(|error| SdkError::io("seek gallery instance marker", error))?;
    let mut marker = String::with_capacity(marker_len as usize);
    lock_file
        .read_to_string(&mut marker)
        .map_err(|error| SdkError::io("read gallery instance marker", error))?;
    if marker != device_instance_id {
        return Err(SdkError::conflict(
            "gallery storage belongs to another physical device instance",
        ));
    }
    Ok(())
}

async fn open_synced_database(
    path: &Path,
    provisioning: &AttendanceProvisioning,
    existing_replica: bool,
) -> SdkResult<(Database, AttendanceSyncState)> {
    let database = build_synced_database(path, provisioning).await?;
    match database.sync().await {
        Ok(_) => Ok((database, AttendanceSyncState::Ready)),
        Err(error) if existing_replica => Ok((database, classify_open_sync_error(&error))),
        Err(error) => Err(SdkError::sync(format!(
            "first gallery bootstrap failed: {error}"
        ))),
    }
}

async fn build_synced_database(
    path: &Path,
    provisioning: &AttendanceProvisioning,
) -> SdkResult<Database> {
    Builder::new_synced_database(
        path,
        provisioning.sync_url.clone(),
        provisioning.auth_token.clone(),
    )
    .remote_writes(false)
    .read_your_writes(true)
    .build()
    .await
    .map_err(|error| SdkError::database(format!("open synchronized gallery: {error}")))
}

fn classify_open_sync_error(error: &libsql::Error) -> AttendanceSyncState {
    if is_writer_forbidden(&error.to_string()) {
        AttendanceSyncState::WriterRevoked
    } else {
        AttendanceSyncState::Offline
    }
}

async fn open_local_database(path: &Path, gallery_id: &str) -> SdkResult<Database> {
    let existed = path.exists();
    let database = Builder::new_local(path)
        .build()
        .await
        .map_err(|error| SdkError::database(format!("open local gallery: {error}")))?;
    if !existed {
        initialize_local_schema(&database, gallery_id).await?;
    }
    Ok(database)
}

async fn initialize_local_schema(database: &Database, gallery_id: &str) -> SdkResult<()> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect local gallery: {error}")))?;
    connection
        .execute_batch(LOCAL_GALLERY_SCHEMA)
        .await
        .map_err(|error| SdkError::database(format!("create local gallery schema: {error}")))?;
    let stream_id = format!("gallery:{gallery_id}");
    for (key, value) in [
        ("schema", GALLERY_SCHEMA),
        ("schema_version", GALLERY_SCHEMA_VERSION),
        ("stream_id", stream_id.as_str()),
        ("gallery_id", gallery_id),
        ("gallery_revision", "0"),
    ] {
        connection
            .execute(
                "INSERT INTO sync_metadata (key, value) VALUES (?1, ?2)",
                params![key, value],
            )
            .await
            .map_err(|error| {
                SdkError::database(format!("insert local gallery metadata: {error}"))
            })?;
    }
    Ok(())
}

async fn load_gallery_rows(
    database: &Database,
    device_instance_id: &str,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<GalleryRows> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect gallery: {error}")))?;
    connection
        .execute("PRAGMA foreign_keys = ON", ())
        .await
        .map_err(|error| SdkError::database(format!("enable gallery foreign keys: {error}")))?;
    let metadata = query_metadata(&connection).await?;
    let schema = metadata_value(&metadata, "schema")?;
    if schema != GALLERY_SCHEMA {
        return Err(SdkError::schema_unsupported(format!(
            "expected schema {GALLERY_SCHEMA}, found {schema}"
        )));
    }
    let version = metadata_value(&metadata, "schema_version")?;
    if version != GALLERY_SCHEMA_VERSION {
        return Err(SdkError::schema_unsupported(format!(
            "gallery schema {version} is not supported; expected {GALLERY_SCHEMA_VERSION}"
        )));
    }
    let gallery_id = metadata_value(&metadata, "gallery_id")?.to_owned();
    validate_identifier("gallery_id", &gallery_id)?;
    let gallery_revision = metadata_value(&metadata, "gallery_revision")?
        .parse::<u64>()
        .map_err(|_| SdkError::integrity("gallery_revision is not an unsigned integer"))?;
    let templates = load_template_store(&connection, device_instance_id, extractor, limits).await?;
    Ok(GalleryRows {
        gallery_id,
        gallery_revision,
        templates,
    })
}

async fn query_metadata(connection: &Connection) -> SdkResult<Vec<(String, String)>> {
    let mut rows = connection
        .query("SELECT key, value FROM sync_metadata ORDER BY key", ())
        .await
        .map_err(|error| SdkError::database(format!("query gallery metadata: {error}")))?;
    let mut metadata = Vec::new();
    while let Some(row) = rows
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read gallery metadata: {error}")))?
    {
        let key = row
            .get::<String>(0)
            .map_err(|error| SdkError::database(format!("decode metadata key: {error}")))?;
        let value = row
            .get::<String>(1)
            .map_err(|error| SdkError::database(format!("decode metadata value: {error}")))?;
        metadata.push((key, value));
    }
    Ok(metadata)
}

fn metadata_value<'a>(metadata: &'a [(String, String)], key: &str) -> SdkResult<&'a str> {
    metadata
        .iter()
        .find(|(candidate, _)| candidate == key)
        .map(|(_, value)| value.as_str())
        .ok_or_else(|| SdkError::integrity(format!("gallery metadata {key} is missing")))
}

async fn load_template_store(
    connection: &Connection,
    device_instance_id: &str,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<TemplateStore> {
    let mut store = TemplateStore::new();
    let mut canonical = connection
        .query(
            r#"SELECT t.subject_id, t.sdk_format_version, t.extractor_profile,
                    t.template_payload, t.payload_sha256
               FROM gallery_templates t
               JOIN gallery_members member ON member.subject_id = t.subject_id
              WHERE t.modality = 'fingerprint' AND member.effective_to IS NULL
              ORDER BY t.subject_id"#,
            (),
        )
        .await
        .map_err(|error| SdkError::database(format!("query gallery templates: {error}")))?;
    while let Some(row) = canonical
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read gallery template: {error}")))?
    {
        let artifact = decode_artifact_row(row, extractor, limits)?;
        replace_store_user(&mut store, artifact)?;
    }

    let mut pending = connection
        .query(
            r#"SELECT s.subject_id, s.sdk_format_version, s.extractor_profile,
                    s.candidate_payload, s.payload_sha256
               FROM enrollment_submissions s
               LEFT JOIN enrollment_results result ON result.submission_id = s.id
               JOIN gallery_members member ON member.subject_id = s.subject_id
              WHERE s.modality = 'fingerprint'
                AND s.device_instance_id = ?1
                AND result.submission_id IS NULL
                AND member.effective_to IS NULL
              ORDER BY s.created_at, s.id"#,
            params![device_instance_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("query pending enrollment: {error}")))?;
    while let Some(row) = pending
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read pending enrollment: {error}")))?
    {
        let artifact = decode_artifact_row(row, extractor, limits)?;
        replace_store_user(&mut store, artifact)?;
    }
    Ok(store)
}

fn decode_artifact_row(
    row: libsql::Row,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<TemplateStore> {
    let subject_id = row
        .get::<String>(0)
        .map_err(|error| SdkError::database(format!("decode template subject: {error}")))?;
    let format_version = row
        .get::<String>(1)
        .map_err(|error| SdkError::database(format!("decode template format: {error}")))?;
    let extractor_profile = row
        .get::<String>(2)
        .map_err(|error| SdkError::database(format!("decode extractor profile: {error}")))?;
    let payload = row
        .get::<Vec<u8>>(3)
        .map_err(|error| SdkError::database(format!("decode template payload: {error}")))?;
    let checksum = row
        .get::<String>(4)
        .map_err(|error| SdkError::database(format!("decode template checksum: {error}")))?;
    decode_subject_template_artifact(
        TemplateArtifactRef {
            subject_id: &subject_id,
            format_version: &format_version,
            extractor_profile: &extractor_profile,
            payload: &payload,
            checksum: &checksum,
        },
        extractor,
        limits,
    )
}

fn replace_store_user(store: &mut TemplateStore, artifact: TemplateStore) -> SdkResult<()> {
    let user_id = artifact.single_user_id()?.to_owned();
    store.remove_user(&user_id);
    for template in artifact.templates() {
        store.upsert(template)?;
    }
    Ok(())
}

async fn insert_enrollment_batch(database: &Database, batch: &EnrollmentBatch) -> SdkResult<()> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect enrollment batch: {error}")))?;
    if query_active_enrollment_batch(database, &batch.device_instance_id)
        .await?
        .is_some()
    {
        return Err(SdkError::session_active(
            "an enrollment batch is already active",
        ));
    }
    connection
        .execute(
            r#"INSERT INTO enrollment_batches (
                id, device_instance_id, performed_by, authorization_id,
                authorization_expires_at, status, started_at, closed_at
             ) VALUES (?1, ?2, ?3, ?4, ?5, 'active', ?6, NULL)"#,
            params![
                batch.id.as_str(),
                batch.device_instance_id.as_str(),
                batch.performed_by.as_str(),
                batch.authorization_id.as_str(),
                batch.authorization_expires_at.as_str(),
                batch.started_at.as_str()
            ],
        )
        .await
        .map_err(|error| {
            if error.to_string().contains("UNIQUE constraint failed") {
                SdkError::conflict("enrollment batch authorization was already used")
            } else {
                SdkError::database(format!("insert enrollment batch: {error}"))
            }
        })?;
    Ok(())
}

async fn query_active_enrollment_batch(
    database: &Database,
    device_instance_id: &str,
) -> SdkResult<Option<EnrollmentBatch>> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect enrollment batch query: {error}")))?;
    let mut rows = connection
        .query(
            r#"SELECT id, device_instance_id, performed_by, authorization_id,
                      authorization_expires_at, status, started_at, closed_at
               FROM enrollment_batches
              WHERE device_instance_id = ?1 AND status = 'active'
              LIMIT 1"#,
            params![device_instance_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("query active enrollment batch: {error}")))?;
    let Some(row) = rows
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read active enrollment batch: {error}")))?
    else {
        return Ok(None);
    };
    Ok(Some(EnrollmentBatch {
        id: row
            .get(0)
            .map_err(|error| SdkError::database(format!("decode batch id: {error}")))?,
        device_instance_id: row
            .get(1)
            .map_err(|error| SdkError::database(format!("decode batch device: {error}")))?,
        performed_by: row
            .get(2)
            .map_err(|error| SdkError::database(format!("decode batch operator: {error}")))?,
        authorization_id: row
            .get(3)
            .map_err(|error| SdkError::database(format!("decode batch authorization: {error}")))?,
        authorization_expires_at: row
            .get(4)
            .map_err(|error| SdkError::database(format!("decode batch expiry: {error}")))?,
        status: row
            .get(5)
            .map_err(|error| SdkError::database(format!("decode batch status: {error}")))?,
        started_at: row
            .get(6)
            .map_err(|error| SdkError::database(format!("decode batch start: {error}")))?,
        closed_at: row
            .get(7)
            .map_err(|error| SdkError::database(format!("decode batch close: {error}")))?,
    }))
}

async fn close_enrollment_batch(
    database: &Database,
    batch_id: &str,
    device_instance_id: &str,
    status: &str,
) -> SdkResult<EnrollmentBatch> {
    let active = query_active_enrollment_batch(database, device_instance_id)
        .await?
        .filter(|batch| batch.id == batch_id)
        .ok_or_else(|| SdkError::not_found("active enrollment batch was not found"))?;
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect enrollment batch close: {error}")))?;
    let closed_at = now_text();
    let changed = connection
        .execute(
            r#"UPDATE enrollment_batches
                SET status = ?2, closed_at = ?3
              WHERE id = ?1 AND device_instance_id = ?4 AND status = 'active'"#,
            params![batch_id, status, closed_at.as_str(), device_instance_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("close enrollment batch: {error}")))?;
    if changed != 1 {
        return Err(SdkError::not_found("active enrollment batch was not found"));
    }
    Ok(EnrollmentBatch {
        id: batch_id.to_owned(),
        device_instance_id: device_instance_id.to_owned(),
        performed_by: active.performed_by,
        authorization_id: active.authorization_id,
        authorization_expires_at: active.authorization_expires_at,
        status: status.to_owned(),
        started_at: active.started_at,
        closed_at: Some(closed_at),
    })
}

struct EnrollmentSubmissionRow<'a> {
    id: &'a str,
    batch_id: Option<&'a str>,
    device_instance_id: &'a str,
    subject_id: &'a str,
    enrollment_operation_id: &'a str,
    performed_by: &'a str,
    authorization_expires_at: &'a str,
    gallery_revision: u64,
    payload: &'a [u8],
    checksum: &'a str,
}

async fn insert_enrollment_submission(
    database: &Database,
    submission: EnrollmentSubmissionRow<'_>,
) -> SdkResult<()> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect enrollment submission: {error}")))?;
    connection
        .execute("PRAGMA foreign_keys = ON", ())
        .await
        .map_err(|error| SdkError::database(format!("enable enrollment foreign keys: {error}")))?;
    let transaction = connection
        .transaction_with_behavior(TransactionBehavior::Immediate)
        .await
        .map_err(|error| SdkError::database(format!("begin enrollment submission: {error}")))?;
    ensure_not_expired(
        "subject enrollment authorization",
        submission.authorization_expires_at,
    )?;
    let mut members = transaction
        .query(
            r#"SELECT count(*)
               FROM gallery_members
              WHERE subject_id = ?1 AND effective_to IS NULL"#,
            params![submission.subject_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("query enrollment membership: {error}")))?;
    let row = members
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read enrollment membership: {error}")))?
        .ok_or_else(|| SdkError::integrity("membership count query returned no row"))?;
    let member_count = row
        .get::<i64>(0)
        .map_err(|error| SdkError::database(format!("decode enrollment membership: {error}")))?;
    drop(members);
    if member_count != 1 {
        return Err(SdkError::conflict(
            "subject is not an active member of this gallery",
        ));
    }
    if let Some(batch_id) = submission.batch_id {
        let mut batches = transaction
            .query(
                r#"SELECT performed_by
                     FROM enrollment_batches
                    WHERE id = ?1 AND device_instance_id = ?2 AND status = 'active'"#,
                params![batch_id, submission.device_instance_id],
            )
            .await
            .map_err(|error| SdkError::database(format!("query enrollment batch: {error}")))?;
        let row = batches
            .next()
            .await
            .map_err(|error| SdkError::database(format!("read enrollment batch: {error}")))?
            .ok_or_else(|| SdkError::conflict("enrollment batch is not active"))?;
        let batch_operator = row
            .get::<String>(0)
            .map_err(|error| SdkError::database(format!("decode batch operator: {error}")))?;
        drop(batches);
        if batch_operator != submission.performed_by {
            return Err(SdkError::conflict(
                "subject authorization administrator does not match the batch",
            ));
        }
    }
    let gallery_revision = i64::try_from(submission.gallery_revision)
        .map_err(|_| SdkError::resource_limit("gallery revision exceeds SQLite INTEGER"))?;
    let now = now_text();
    transaction
        .execute(
            r#"INSERT INTO enrollment_submissions (
                id, batch_id, device_instance_id, subject_id,
                enrollment_operation_id, performed_by, authorization_expires_at, modality,
                observed_gallery_revision, sdk_format_version, extractor_profile,
                candidate_payload, payload_sha256, captured_at, created_at
             ) VALUES (
                ?1, ?2, ?3, ?4, ?5, ?6, ?7, 'fingerprint',
                ?8, ?9, ?10, ?11, ?12, ?13, ?13
             )"#,
            params![
                submission.id,
                submission.batch_id,
                submission.device_instance_id,
                submission.subject_id,
                submission.enrollment_operation_id,
                submission.performed_by,
                submission.authorization_expires_at,
                gallery_revision,
                TEMPLATE_FORMAT_VERSION,
                DEFAULT_EXTRACTOR_PROFILE,
                submission.payload,
                submission.checksum,
                now.as_str(),
            ],
        )
        .await
        .map_err(|error| {
            if error.to_string().contains("UNIQUE constraint failed") {
                SdkError::conflict("enrollment operation authorization was already used")
            } else {
                SdkError::database(format!("insert enrollment submission: {error}"))
            }
        })?;
    transaction
        .commit()
        .await
        .map_err(|error| SdkError::database(format!("commit enrollment submission: {error}")))
}

async fn active_gallery_member_exists(database: &Database, subject_id: &str) -> SdkResult<bool> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect gallery membership: {error}")))?;
    let mut rows = connection
        .query(
            r#"SELECT count(*)
                 FROM gallery_members
                WHERE subject_id = ?1 AND effective_to IS NULL"#,
            params![subject_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("query gallery membership: {error}")))?;
    let row = rows
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read gallery membership: {error}")))?
        .ok_or_else(|| SdkError::integrity("membership count query returned no row"))?;
    let count = row
        .get::<i64>(0)
        .map_err(|error| SdkError::database(format!("decode gallery membership: {error}")))?;
    Ok(count == 1)
}

async fn query_pending_enrollment_count(
    database: &Database,
    device_instance_id: &str,
) -> SdkResult<usize> {
    let connection = database.connect().map_err(|error| {
        SdkError::database(format!("connect pending enrollment count: {error}"))
    })?;
    let mut rows = connection
        .query(
            r#"SELECT count(*)
                 FROM enrollment_submissions submission
                 LEFT JOIN enrollment_results result
                   ON result.submission_id = submission.id
                WHERE submission.device_instance_id = ?1
                  AND result.submission_id IS NULL"#,
            params![device_instance_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("query pending enrollment count: {error}")))?;
    let row = rows
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read pending enrollment count: {error}")))?
        .ok_or_else(|| SdkError::integrity("pending enrollment count query returned no row"))?;
    let count = row
        .get::<i64>(0)
        .map_err(|error| SdkError::database(format!("decode pending enrollment count: {error}")))?;
    usize::try_from(count)
        .map_err(|_| SdkError::integrity("pending enrollment count is negative or too large"))
}

fn prepare_enrollment<I, R>(
    gallery_id: &str,
    subject_id: &str,
    captures: I,
    existing_templates: &[ExtractedTemplate],
    config: EnrollmentConfig,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<(Option<TemplateStore>, EnrollmentReport)>
where
    I: IntoIterator<Item = R>,
    R: AsRef<[u8]>,
{
    let mut attempts = Vec::new();
    let mut accepted = Vec::new();
    let mut duplicate_found = false;
    for raw in captures {
        if accepted.len() >= config.max_templates_per_subject {
            attempts.push(rejected_attempt(
                subject_id,
                None,
                EnrollmentRejectionReason::MaxTemplatesForSubject {
                    max_templates: config.max_templates_per_subject,
                },
            ));
            continue;
        }
        let record_id = Uuid::now_v7().to_string();
        let template = match extract_raw_bytes(
            record_id.clone(),
            subject_id.to_owned(),
            raw.as_ref(),
            extractor,
        ) {
            Ok(template) => template,
            Err(error) => {
                attempts.push(rejected_attempt(
                    subject_id,
                    None,
                    EnrollmentRejectionReason::InvalidCapture {
                        message: error.to_string(),
                    },
                ));
                continue;
            }
        };
        if template.quality < config.min_quality {
            attempts.push(rejected_attempt(
                subject_id,
                Some(template.quality),
                EnrollmentRejectionReason::LowQuality {
                    quality: template.quality,
                    min_quality: config.min_quality,
                },
            ));
            continue;
        }
        if let Some(duplicate) =
            duplicate_match(&template, existing_templates, config, extractor, limits)?
        {
            attempts.push(rejected_attempt(
                subject_id,
                Some(template.quality),
                EnrollmentRejectionReason::DuplicateOfOtherSubject { duplicate },
            ));
            duplicate_found = true;
            break;
        }
        attempts.push(EnrollmentAttempt {
            subject_id: subject_id.to_owned(),
            record_id: Some(record_id),
            quality: Some(template.quality),
            accepted: true,
            rejection: None,
        });
        accepted.push(template);
    }
    if duplicate_found {
        for attempt in &mut attempts {
            if attempt.accepted {
                attempt.accepted = false;
                attempt.record_id = None;
                attempt.rejection = Some(EnrollmentRejectionReason::NotCommitted {
                    message: "batch contained a cross-user duplicate".to_owned(),
                });
            }
        }
        accepted.clear();
    }
    let report = EnrollmentReport {
        gallery_id: gallery_id.to_owned(),
        accepted_records: accepted.len(),
        accepted_subjects: usize::from(!accepted.is_empty()),
        rejected_captures: attempts.iter().filter(|attempt| !attempt.accepted).count(),
        attempts,
    };
    if accepted.is_empty() {
        return Ok((None, report));
    }
    Ok((Some(TemplateStore::from_templates(accepted)?), report))
}

fn duplicate_match(
    template: &ExtractedTemplate,
    existing_templates: &[ExtractedTemplate],
    config: EnrollmentConfig,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<Option<DuplicateEnrollmentMatch>> {
    if !config.duplicate.enabled {
        return Ok(None);
    }
    let candidates = existing_templates
        .iter()
        .filter(|existing| existing.record.user_id != template.record.user_id)
        .cloned()
        .collect::<Vec<_>>();
    if candidates.is_empty() {
        return Ok(None);
    }
    let index = BiometricIndex::build_with_config(&candidates, extractor, limits)?;
    Ok(index
        .search_users(template, config.duplicate.search)?
        .into_iter()
        .find(|hit| {
            hit.score >= config.duplicate.min_score
                && hit.verification_score >= config.duplicate.min_verification_score
        })
        .map(|hit| DuplicateEnrollmentMatch {
            subject_id: hit.user_id,
            record_id: hit.record_id,
            score: hit.score,
            verification_score: hit.verification_score,
        }))
}

fn rejected_attempt(
    subject_id: &str,
    quality: Option<u8>,
    rejection: EnrollmentRejectionReason,
) -> EnrollmentAttempt {
    EnrollmentAttempt {
        subject_id: subject_id.to_owned(),
        record_id: None,
        quality,
        accepted: false,
        rejection: Some(rejection),
    }
}

fn now_text() -> String {
    Utc::now().to_rfc3339_opts(SecondsFormat::Millis, true)
}

fn ensure_not_expired(label: &str, expires_at: &str) -> SdkResult<()> {
    ensure_not_expired_at(label, expires_at, Utc::now())
}

fn ensure_not_expired_at(
    label: &str,
    expires_at: &str,
    observed_at: chrono::DateTime<Utc>,
) -> SdkResult<()> {
    let expires_at = chrono::DateTime::parse_from_rfc3339(expires_at)
        .map_err(|_| SdkError::invalid_input(format!("{label} expiry must be RFC3339")))?;
    if observed_at > expires_at {
        return Err(SdkError::conflict(format!("{label} has expired")));
    }
    Ok(())
}

fn validate_batch_authorization(
    authorization: &EnrollmentBatchAuthorization,
    device_instance_id: &str,
    gallery_id: &str,
) -> SdkResult<()> {
    validate_identifier("authorization_id", &authorization.authorization_id)?;
    validate_identifier("performed_by", &authorization.performed_by)?;
    validate_identifier("device_instance_id", &authorization.device_instance_id)?;
    validate_identifier("gallery_id", &authorization.gallery_id)?;
    ensure_not_expired(
        "enrollment batch authorization",
        &authorization.authorization_expires_at,
    )?;
    if authorization.device_instance_id != device_instance_id {
        return Err(SdkError::conflict(
            "batch authorization belongs to another device instance",
        ));
    }
    if authorization.gallery_id != gallery_id {
        return Err(SdkError::conflict(
            "batch authorization belongs to another gallery",
        ));
    }
    Ok(())
}

fn validate_subject_authorization(
    authorization: &SubjectEnrollmentAuthorization,
    device_instance_id: &str,
    gallery_id: &str,
) -> SdkResult<()> {
    validate_identifier(
        "enrollment_operation_id",
        &authorization.enrollment_operation_id,
    )?;
    validate_identifier("performed_by", &authorization.performed_by)?;
    validate_identifier("device_instance_id", &authorization.device_instance_id)?;
    validate_identifier("gallery_id", &authorization.gallery_id)?;
    validate_identifier("subject_id", &authorization.subject_id)?;
    if let Some(batch_id) = &authorization.batch_id {
        validate_identifier("batch_id", batch_id)?;
    }
    ensure_not_expired(
        "subject enrollment authorization",
        &authorization.authorization_expires_at,
    )?;
    if authorization.device_instance_id != device_instance_id {
        return Err(SdkError::conflict(
            "subject authorization belongs to another device instance",
        ));
    }
    if authorization.gallery_id != gallery_id {
        return Err(SdkError::conflict(
            "subject authorization belongs to another gallery",
        ));
    }
    Ok(())
}

async fn ensure_enrollment_authorized(
    database: &Database,
    authorization: &SubjectEnrollmentAuthorization,
    device_instance_id: &str,
) -> SdkResult<()> {
    if !active_gallery_member_exists(database, &authorization.subject_id).await? {
        return Err(SdkError::conflict(
            "subject is not an active member of this gallery; synchronize before enrollment",
        ));
    }
    let connection = database.connect().map_err(|error| {
        SdkError::database(format!("connect enrollment authorization: {error}"))
    })?;
    let mut operations = connection
        .query(
            "SELECT count(*) FROM enrollment_submissions WHERE enrollment_operation_id = ?1",
            params![authorization.enrollment_operation_id.as_str()],
        )
        .await
        .map_err(|error| SdkError::database(format!("query enrollment operation: {error}")))?;
    let operation_count = operations
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read enrollment operation: {error}")))?
        .ok_or_else(|| SdkError::integrity("enrollment operation count returned no row"))?
        .get::<i64>(0)
        .map_err(|error| SdkError::database(format!("decode enrollment operation: {error}")))?;
    drop(operations);
    if operation_count != 0 {
        return Err(SdkError::conflict(
            "enrollment operation authorization was already used",
        ));
    }
    if let Some(batch_id) = &authorization.batch_id {
        let batch = query_active_enrollment_batch(database, device_instance_id)
            .await?
            .filter(|batch| batch.id == *batch_id)
            .ok_or_else(|| SdkError::conflict("enrollment batch is not active"))?;
        if batch.performed_by != authorization.performed_by {
            return Err(SdkError::conflict(
                "subject authorization administrator does not match the batch",
            ));
        }
    }
    Ok(())
}

fn is_writer_forbidden(message: &str) -> bool {
    let message = message.to_ascii_lowercase();
    message.contains("gallery_writer_forbidden")
        || message.contains("writer forbidden")
        || message.contains("status 403")
        || message.contains("status: 403")
}

fn ensure_enrollment_state(state: AttendanceSyncState) -> SdkResult<()> {
    match state {
        AttendanceSyncState::Ready | AttendanceSyncState::Offline => Ok(()),
        AttendanceSyncState::WriterRevoked => {
            Err(SdkError::conflict("gallery writer authority was revoked"))
        }
        AttendanceSyncState::Quarantined => Err(SdkError::integrity(
            "gallery state is quarantined pending a valid synchronization",
        )),
    }
}

const LOCAL_GALLERY_SCHEMA: &str = r#"
PRAGMA page_size = 4096;
PRAGMA foreign_keys = ON;
CREATE TABLE sync_metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
) STRICT;
CREATE TABLE gallery_members (
    subject_id TEXT PRIMARY KEY,
    membership_id TEXT NOT NULL,
    effective_from TEXT NOT NULL,
    effective_to TEXT,
    profile_revision INTEGER CHECK (profile_revision IS NULL OR profile_revision > 0),
    updated_at TEXT NOT NULL
) STRICT;
CREATE TABLE gallery_templates (
    subject_id TEXT NOT NULL,
    modality TEXT NOT NULL CHECK (modality IN ('fingerprint', 'face')),
    profile_revision INTEGER NOT NULL CHECK (profile_revision > 0),
    sdk_format_version TEXT NOT NULL,
    extractor_profile TEXT NOT NULL,
    template_payload BLOB NOT NULL CHECK (length(template_payload) > 0),
    payload_sha256 TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (subject_id, modality),
    FOREIGN KEY (subject_id) REFERENCES gallery_members(subject_id) ON DELETE CASCADE
) STRICT;
CREATE TABLE enrollment_batches (
    id TEXT PRIMARY KEY,
    device_instance_id TEXT NOT NULL,
    performed_by TEXT NOT NULL,
    authorization_id TEXT NOT NULL UNIQUE,
    authorization_expires_at TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('active', 'closed', 'cancelled')),
    started_at TEXT NOT NULL,
    closed_at TEXT,
    CHECK (
        (status = 'active' AND closed_at IS NULL)
        OR (status IN ('closed', 'cancelled') AND closed_at IS NOT NULL)
    )
) STRICT;
CREATE UNIQUE INDEX enrollment_batches_one_active_idx
    ON enrollment_batches (device_instance_id) WHERE status = 'active';
CREATE TABLE enrollment_submissions (
    id TEXT PRIMARY KEY,
    batch_id TEXT,
    device_instance_id TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    enrollment_operation_id TEXT NOT NULL UNIQUE,
    performed_by TEXT NOT NULL,
    authorization_expires_at TEXT NOT NULL,
    modality TEXT NOT NULL CHECK (modality IN ('fingerprint', 'face')),
    observed_gallery_revision INTEGER NOT NULL CHECK (observed_gallery_revision >= 0),
    sdk_format_version TEXT NOT NULL,
    extractor_profile TEXT NOT NULL,
    candidate_payload BLOB NOT NULL CHECK (length(candidate_payload) > 0),
    payload_sha256 TEXT NOT NULL,
    captured_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES enrollment_batches(id),
    FOREIGN KEY (subject_id) REFERENCES gallery_members(subject_id)
) STRICT;
CREATE INDEX enrollment_submissions_subject_idx
    ON enrollment_submissions (subject_id, created_at, id);
CREATE TABLE enrollment_results (
    submission_id TEXT PRIMARY KEY,
    status TEXT NOT NULL CHECK (status IN ('accepted', 'rejected')),
    reason_code TEXT,
    canonical_profile_revision INTEGER CHECK (
        canonical_profile_revision IS NULL OR canonical_profile_revision > 0
    ),
    decided_at TEXT NOT NULL,
    FOREIGN KEY (submission_id) REFERENCES enrollment_submissions(id) ON DELETE CASCADE,
    CHECK (
        (status = 'accepted' AND canonical_profile_revision IS NOT NULL AND reason_code IS NULL)
        OR (status = 'rejected' AND reason_code IS NOT NULL)
    )
) STRICT;
"#;

#[cfg(test)]
mod tests {
    use super::*;

    fn temporary_root() -> PathBuf {
        std::env::temp_dir().join(format!("biometric-attendance-test-{}", Uuid::now_v7()))
    }

    fn ridge_pattern(seed: u8) -> Vec<u8> {
        let mut raw = vec![0_u8; crate::fingerprint::RAW_LEN];
        for y in 0..crate::fingerprint::RAW_HEIGHT as usize {
            for x in 0..crate::fingerprint::RAW_WIDTH as usize {
                let wave = ((x + usize::from(seed) * y / 17) / 5) % 2;
                raw[y * crate::fingerprint::RAW_WIDTH as usize + x] =
                    if wave == 0 { 35 } else { 220 };
            }
        }
        raw
    }

    fn local_sdk(root: &Path) -> AttendanceBiometricSdk {
        local_sdk_for_device(root, "device-1")
    }

    fn local_sdk_for_device(root: &Path, device_instance_id: &str) -> AttendanceBiometricSdk {
        AttendanceBiometricSdk::open_local_with_config(
            root.to_path_buf(),
            "gallery:population:a".to_owned(),
            device_instance_id.to_owned(),
            EnrollmentConfig::default().with_min_quality(0),
            ExtractorConfig::default(),
            IdentifyConfig {
                min_quality: 0,
                min_score: 0.0,
                min_verification_score: 0.0,
                min_margin: 0.0,
                ..IdentifyConfig::default()
            },
            SdkLimits::default(),
        )
        .unwrap()
    }

    fn add_gallery_member(sdk: &AttendanceBiometricSdk, subject_id: &str) {
        let state = sdk.database().unwrap();
        let connection = state.database().unwrap().connect().unwrap();
        let now = now_text();
        sdk.inner
            .runtime
            .block_on(connection.execute(
                r#"INSERT INTO gallery_members (
                    subject_id, membership_id, effective_from, effective_to, updated_at
                 ) VALUES (?1, ?2, ?3, NULL, ?3)"#,
                params![subject_id, format!("membership-{subject_id}"), now],
            ))
            .unwrap();
    }

    fn corrupt_submission_checksum(sdk: &AttendanceBiometricSdk, submission_id: &str) {
        let state = sdk.database().unwrap();
        let connection = state.database().unwrap().connect().unwrap();
        sdk.inner
            .runtime
            .block_on(connection.execute(
                "UPDATE enrollment_submissions SET payload_sha256 = 'sha256:invalid' WHERE id = ?1",
                params![submission_id],
            ))
            .unwrap();
    }

    fn future_expiry() -> String {
        (Utc::now() + chrono::Duration::minutes(5)).to_rfc3339_opts(SecondsFormat::Millis, true)
    }

    fn batch_authorization(device_instance_id: &str) -> EnrollmentBatchAuthorization {
        EnrollmentBatchAuthorization {
            authorization_id: Uuid::now_v7().to_string(),
            performed_by: "admin-1".to_owned(),
            device_instance_id: device_instance_id.to_owned(),
            gallery_id: "gallery:population:a".to_owned(),
            authorization_expires_at: future_expiry(),
        }
    }

    fn subject_authorization(
        device_instance_id: &str,
        subject_id: &str,
        batch_id: Option<String>,
    ) -> SubjectEnrollmentAuthorization {
        SubjectEnrollmentAuthorization {
            enrollment_operation_id: Uuid::now_v7().to_string(),
            performed_by: "admin-1".to_owned(),
            device_instance_id: device_instance_id.to_owned(),
            gallery_id: "gallery:population:a".to_owned(),
            subject_id: subject_id.to_owned(),
            batch_id,
            authorization_expires_at: future_expiry(),
        }
    }

    #[test]
    fn group_enrollment_is_durable_and_provisional_after_restart() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        add_gallery_member(&sdk, "subject-1");
        let batch = sdk
            .start_enrollment_batch(batch_authorization("device-1"))
            .unwrap();
        let result = sdk
            .enroll_subject(
                subject_authorization("device-1", "subject-1", Some(batch.id.clone())),
                [ridge_pattern(3)],
            )
            .unwrap();
        assert!(result.submission_id.is_some());
        assert_eq!(sdk.gallery_stats().subjects, 1);
        assert_eq!(sdk.pending_enrollment_count().unwrap(), 1);
        drop(sdk);

        let reopened = local_sdk(&root);
        assert_eq!(reopened.gallery_stats().subjects, 1);
        assert_eq!(
            reopened.active_enrollment_batch().unwrap().unwrap().id,
            batch.id
        );
        drop(reopened);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn accepted_identification_contains_atomic_gallery_evidence() {
        let gallery = GalleryIndex::build("gallery:population:a", 7, TemplateStore::new()).unwrap();
        let result = attach_gallery_evidence(
            &gallery,
            IdentifyResult::Match(super::super::index::IdentifyMatch {
                user_id: "subject-1".to_owned(),
                record_id: "record-1".to_owned(),
                score: 0.75,
                verification_score: 0.68,
                votes: 12,
            }),
        );
        let AttendanceIdentifyResult::Match(evidence) = result else {
            panic!("accepted core match was not preserved")
        };
        assert_eq!(evidence.subject_id, "subject-1");
        assert_eq!(evidence.record_id, "record-1");
        assert_eq!(evidence.gallery_id, "gallery:population:a");
        assert_eq!(evidence.gallery_revision, 7);
        assert_eq!(evidence.modality, "fingerprint");
        assert_eq!(evidence.score, 0.75);
        assert_eq!(evidence.verification_score, 0.68);
    }

    #[test]
    fn enrollment_requires_current_gallery_membership() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        assert_eq!(
            sdk.enrollment_readiness("subject-1").unwrap(),
            EnrollmentReadiness::GallerySyncRequired
        );
        let error = sdk
            .enroll_subject(
                subject_authorization("device-1", "subject-1", None),
                [ridge_pattern(4)],
            )
            .unwrap_err();
        assert_eq!(error.code(), super::super::error::SdkErrorCode::Conflict);
        drop(sdk);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn enrollment_authorization_is_bound_and_single_use_after_commit() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        add_gallery_member(&sdk, "subject-1");
        assert_eq!(
            sdk.enrollment_readiness("subject-1").unwrap(),
            EnrollmentReadiness::Ready
        );

        let mut wrong_device = subject_authorization("device-2", "subject-1", None);
        assert_eq!(
            sdk.enroll_subject(wrong_device.clone(), [ridge_pattern(2)])
                .unwrap_err()
                .code(),
            super::super::error::SdkErrorCode::Conflict
        );
        wrong_device.device_instance_id = "device-1".to_owned();
        let authorization = wrong_device;
        sdk.enroll_subject(authorization.clone(), [ridge_pattern(2)])
            .unwrap();
        assert_eq!(
            sdk.enroll_subject(authorization, [ridge_pattern(2)])
                .unwrap_err()
                .code(),
            super::super::error::SdkErrorCode::Conflict
        );

        drop(sdk);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn expired_authorization_is_rejected_before_capture() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        add_gallery_member(&sdk, "subject-1");
        let mut authorization = subject_authorization("device-1", "subject-1", None);
        authorization.authorization_expires_at = (Utc::now() - chrono::Duration::seconds(1))
            .to_rfc3339_opts(SecondsFormat::Millis, true);
        assert_eq!(
            sdk.enroll_subject(authorization, [ridge_pattern(2)])
                .unwrap_err()
                .code(),
            super::super::error::SdkErrorCode::Conflict
        );
        drop(sdk);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn authorization_expiry_boundary_is_inclusive() {
        let expiry = "2030-01-01T00:00:00.000Z";
        let boundary = chrono::DateTime::parse_from_rfc3339(expiry)
            .unwrap()
            .with_timezone(&Utc);
        ensure_not_expired_at("authorization", expiry, boundary).unwrap();
        assert_eq!(
            ensure_not_expired_at(
                "authorization",
                expiry,
                boundary + chrono::Duration::nanoseconds(1),
            )
            .unwrap_err()
            .code(),
            super::super::error::SdkErrorCode::Conflict
        );
    }

    #[test]
    fn an_expired_batch_remains_an_organizer_for_fresh_subject_authorization() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        add_gallery_member(&sdk, "subject-1");
        let batch = sdk
            .start_enrollment_batch(batch_authorization("device-1"))
            .unwrap();
        let state = sdk.database().unwrap();
        let connection = state.database().unwrap().connect().unwrap();
        sdk.inner
            .runtime
            .block_on(connection.execute(
                r#"UPDATE enrollment_batches
                      SET started_at = '2025-01-01T00:00:00.000Z',
                          authorization_expires_at = '2025-01-01T00:01:00.000Z'
                    WHERE id = ?1"#,
                params![batch.id.as_str()],
            ))
            .unwrap();
        drop(state);

        let result = sdk
            .enroll_subject(
                subject_authorization("device-1", "subject-1", Some(batch.id)),
                [ridge_pattern(2)],
            )
            .unwrap();
        assert!(result.submission_id.is_some());
        drop(sdk);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn storage_root_is_bound_to_one_physical_device_instance() {
        let root = temporary_root();
        drop(local_sdk_for_device(&root, "device-1"));
        let error = AttendanceBiometricSdk::open_local(&root, "gallery:population:a", "device-2")
            .unwrap_err();
        assert_eq!(error.code(), super::super::error::SdkErrorCode::Conflict);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn only_one_group_batch_can_be_active() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        let batch = sdk
            .start_enrollment_batch(batch_authorization("device-1"))
            .unwrap();
        assert_eq!(
            sdk.start_enrollment_batch(batch_authorization("device-1"))
                .unwrap_err()
                .code(),
            super::super::error::SdkErrorCode::SessionActive
        );
        let closed = sdk.close_enrollment_batch(&batch.id).unwrap();
        assert_eq!(closed.started_at, batch.started_at);
        assert!(sdk.active_enrollment_batch().unwrap().is_none());
        drop(sdk);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn replacement_writer_can_start_while_old_device_batch_is_preserved() {
        let original_root = temporary_root();
        let replacement_root = temporary_root();
        let original = local_sdk_for_device(&original_root, "device-1");
        let original_batch = original
            .start_enrollment_batch(batch_authorization("device-1"))
            .unwrap();
        drop(original);

        let replacement = local_sdk_for_device(&replacement_root, "device-2");
        assert!(replacement.active_enrollment_batch().unwrap().is_none());
        let replacement_batch = replacement
            .start_enrollment_batch(batch_authorization("device-2"))
            .unwrap();
        assert_eq!(replacement_batch.device_instance_id, "device-2");
        drop(replacement);

        let reopened_original = local_sdk_for_device(&original_root, "device-1");
        assert_eq!(
            reopened_original
                .active_enrollment_batch()
                .unwrap()
                .unwrap()
                .id,
            original_batch.id
        );
        assert_ne!(replacement_batch.id, original_batch.id);
        drop(reopened_original);
        fs::remove_dir_all(original_root).unwrap();
        fs::remove_dir_all(replacement_root).unwrap();
    }

    #[test]
    fn invalid_synchronized_rows_quarantine_enrollment_but_preserve_matcher() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        add_gallery_member(&sdk, "subject-1");
        add_gallery_member(&sdk, "subject-2");
        let enrollment = sdk
            .enroll_subject(
                subject_authorization("device-1", "subject-1", None),
                [ridge_pattern(5)],
            )
            .unwrap();
        let submission_id = enrollment.submission_id.unwrap();
        assert_eq!(sdk.gallery_stats().subjects, 1);

        corrupt_submission_checksum(&sdk, &submission_id);
        assert_eq!(
            sdk.sync().unwrap_err().code(),
            super::super::error::SdkErrorCode::Integrity
        );
        assert_eq!(sdk.sync_state().unwrap(), AttendanceSyncState::Quarantined);
        assert_eq!(sdk.gallery_stats().subjects, 1);
        assert_eq!(
            sdk.enroll_subject(
                subject_authorization("device-1", "subject-2", None),
                [ridge_pattern(6)],
            )
            .unwrap_err()
            .code(),
            super::super::error::SdkErrorCode::Integrity
        );
        drop(sdk);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn writer_forbidden_detection_accepts_gateway_error_shapes() {
        assert!(is_writer_forbidden("gallery_writer_forbidden"));
        assert!(is_writer_forbidden("HTTP status: 403 Forbidden"));
        assert!(!is_writer_forbidden("HTTP status: 401 Unauthorized"));
    }
}
