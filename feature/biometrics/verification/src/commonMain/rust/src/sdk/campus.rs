//! SDK-owned libSQL gallery persistence and synchronization.
//!
//! [`CampusBiometricSdk`] is the high-level campus API. It owns the replica
//! file, serializes every SQL mutation with libSQL synchronization, and keeps a
//! validated [`GalleryIndex`] published for network-independent matching. The
//! Android application supplies provisioning and biometric commands; it never
//! opens the gallery database or interprets synchronized rows.

use std::fmt::{self, Debug, Formatter};
use std::fs::{self, File, OpenOptions};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, MutexGuard};

use arc_swap::ArcSwap;
use chrono::{SecondsFormat, Utc};
use fs2::FileExt;
use libsql::{Builder, Connection, Database, TransactionBehavior, params};
use tokio::runtime::Runtime;
use uuid::Uuid;

use super::artifact::{
    TemplateArtifactRef, decode_student_template_artifact, template_payload_checksum,
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

const CLASS_GALLERY_SCHEMA: &str = "tanda-class-gallery";
const CLASS_GALLERY_SCHEMA_VERSION: &str = "2";
const REPLICA_FILE: &str = "class-gallery.db";
const REPLICA_LOCK_FILE: &str = "class-gallery.lock";

/// Remote endpoint and credential issued for one device.
#[derive(Clone)]
pub struct CampusProvisioning {
    /// Stable device identifier bound to the bearer credential.
    pub device_id: String,
    /// Base endpoint implementing the official libSQL sync protocol.
    pub sync_url: String,
    /// Bearer credential used only by the libSQL client.
    pub auth_token: String,
}

impl CampusProvisioning {
    /// Construct device provisioning.
    pub fn new(
        device_id: impl Into<String>,
        sync_url: impl Into<String>,
        auth_token: impl Into<String>,
    ) -> Self {
        Self {
            device_id: device_id.into(),
            sync_url: sync_url.into(),
            auth_token: auth_token.into(),
        }
    }

    fn validate(&self) -> SdkResult<()> {
        validate_identifier("device_id", &self.device_id)?;
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

impl Debug for CampusProvisioning {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("CampusProvisioning")
            .field("device_id", &self.device_id)
            .field("sync_url", &self.sync_url)
            .field("auth_token", &"[redacted]")
            .finish()
    }
}

/// Initialization settings for the campus SDK.
#[derive(Debug, Clone)]
pub struct CampusConfig {
    /// SDK-owned writable directory.
    pub storage_root: PathBuf,
    /// Device provisioning for synchronized mode.
    pub provisioning: CampusProvisioning,
    /// Enrollment acceptance policy.
    pub enrollment: EnrollmentConfig,
    /// Fingerprint extraction profile.
    pub extractor: ExtractorConfig,
    /// Identity acceptance policy.
    pub identify: IdentifyConfig,
    /// Allocation and record limits.
    pub limits: SdkLimits,
}

impl CampusConfig {
    /// Construct synchronized campus settings with current biometric defaults.
    pub fn new(storage_root: impl Into<PathBuf>, provisioning: CampusProvisioning) -> Self {
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
pub enum CampusSyncState {
    /// The replica is usable and its latest sync completed.
    Ready,
    /// Existing local state is usable but the latest network sync failed.
    Offline,
    /// The server revoked this device's class-gallery write authority.
    WriterRevoked,
    /// Synchronized rows failed validation; the previous matcher remains live.
    Quarantined,
}

/// Outcome of one synchronization attempt.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CampusSyncReport {
    /// State after the attempt.
    pub state: CampusSyncState,
    /// Number of physical 4 KiB frames transferred by libSQL.
    pub frames_synced: usize,
    /// Current application-level gallery revision.
    pub gallery_revision: u64,
    /// Current indexed student count, including local provisional enrollment.
    pub indexed_users: usize,
    /// Enrollment submissions from this device awaiting a server decision.
    pub pending_enrollments: usize,
}

/// Persisted group-enrollment batch.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EnrollmentBatch {
    /// UUIDv7 batch identifier.
    pub id: String,
    /// Device that owns this resumable batch.
    pub device_id: String,
    /// `active`, `closed`, or `cancelled`.
    pub status: String,
    /// UTC timestamp at which the batch started.
    pub started_at: String,
    /// UTC terminal timestamp when the batch is no longer active.
    pub closed_at: Option<String>,
}

/// Enrollment result after local transaction commit.
#[derive(Debug, Clone, PartialEq)]
pub struct CampusEnrollmentResult {
    /// UUIDv7 submission identifier when at least one capture was accepted.
    pub submission_id: Option<String>,
    /// Group batch containing the submission, when supplied.
    pub batch_id: Option<String>,
    /// Capture-level extraction and acceptance outcomes.
    pub report: EnrollmentReport,
}

/// SDK facade owning one provisioned class gallery.
#[derive(Clone)]
pub struct CampusBiometricSdk {
    inner: Arc<CampusInner>,
}

impl Debug for CampusBiometricSdk {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("CampusBiometricSdk")
            .field("storage_root", &self.inner.storage_root)
            .field("device_id", &self.inner.device_id)
            .field("gallery", &self.inner.gallery.load().stats())
            .finish_non_exhaustive()
    }
}

struct CampusInner {
    storage_root: PathBuf,
    device_id: String,
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
    sync_state: CampusSyncState,
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
            .ok_or_else(|| SdkError::database("class-gallery database is being reconfigured"))
    }
}

struct GalleryRows {
    gallery_id: String,
    gallery_revision: u64,
    templates: TemplateStore,
}

impl CampusBiometricSdk {
    /// Open or bootstrap one synchronized class gallery.
    ///
    /// A new replica requires connectivity. An existing verified replica may
    /// open in [`CampusSyncState::Offline`] when the initial refresh fails.
    pub fn open(config: CampusConfig) -> SdkResult<Self> {
        config.provisioning.validate()?;
        let limits = config.limits.validate()?;
        let enrollment = config.enrollment.validate(limits)?;
        let extractor = config.extractor.validate(limits)?;
        fs::create_dir_all(&config.storage_root).map_err(|error| {
            SdkError::io(
                format!("create campus storage {}", config.storage_root.display()),
                error,
            )
        })?;
        let lock_path = config.storage_root.join(REPLICA_LOCK_FILE);
        let lock_file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .open(&lock_path)
            .map_err(|error| {
                SdkError::io(format!("open campus lock {}", lock_path.display()), error)
            })?;
        lock_file
            .try_lock_exclusive()
            .map_err(|_| SdkError::session_active("another SDK instance owns the class gallery"))?;

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
            &config.provisioning.device_id,
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
            inner: Arc::new(CampusInner {
                storage_root: config.storage_root,
                device_id: config.provisioning.device_id,
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
        device_id: impl Into<String>,
    ) -> SdkResult<Self> {
        Self::open_local_with_config(
            storage_root.into(),
            gallery_id.into(),
            device_id.into(),
            EnrollmentConfig::default(),
            ExtractorConfig::default(),
            IdentifyConfig::default(),
            SdkLimits::default(),
        )
    }

    fn open_local_with_config(
        storage_root: PathBuf,
        gallery_id: String,
        device_id: String,
        enrollment: EnrollmentConfig,
        extractor: ExtractorConfig,
        identify: IdentifyConfig,
        limits: SdkLimits,
    ) -> SdkResult<Self> {
        validate_identifier("gallery_id", &gallery_id)?;
        validate_identifier("device_id", &device_id)?;
        let limits = limits.validate()?;
        let enrollment = enrollment.validate(limits)?;
        let extractor = extractor.validate(limits)?;
        fs::create_dir_all(&storage_root).map_err(|error| {
            SdkError::io(
                format!("create campus storage {}", storage_root.display()),
                error,
            )
        })?;
        let lock_path = storage_root.join(REPLICA_LOCK_FILE);
        let lock_file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .open(&lock_path)
            .map_err(|error| {
                SdkError::io(format!("open campus lock {}", lock_path.display()), error)
            })?;
        lock_file
            .try_lock_exclusive()
            .map_err(|_| SdkError::session_active("another SDK instance owns the class gallery"))?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .map_err(|error| SdkError::database(format!("create SDK runtime: {error}")))?;
        let database = runtime.block_on(open_local_database(
            &storage_root.join(REPLICA_FILE),
            &gallery_id,
            &device_id,
        ))?;
        let rows = runtime.block_on(load_gallery_rows(&database, &device_id, extractor, limits))?;
        let gallery = GalleryIndex::build_with_profiles(
            rows.gallery_id,
            rows.gallery_revision,
            rows.templates,
            extractor,
            identify,
            limits,
        )?;
        Ok(Self {
            inner: Arc::new(CampusInner {
                storage_root,
                device_id,
                runtime,
                database: Mutex::new(DatabaseState {
                    database: Some(database),
                    sync_state: CampusSyncState::Ready,
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
    pub fn sync_state(&self) -> SdkResult<CampusSyncState> {
        Ok(self.database()?.sync_state)
    }

    /// Return current immutable matcher sizing and revision.
    pub fn gallery_stats(&self) -> GalleryStats {
        self.inner.gallery.load().stats()
    }

    /// Match a raw scan without opening the database or waiting for sync.
    pub fn identify_raw_bytes(&self, raw: &[u8]) -> SdkResult<IdentifyResult> {
        self.inner.gallery.load().identify_raw_bytes(raw)
    }

    /// Synchronize local WAL state and publish a rebuilt matcher when needed.
    pub fn sync(&self) -> SdkResult<CampusSyncReport> {
        let _operation = self.operation()?;
        let mut state = self.database()?;
        if state.sync_state == CampusSyncState::WriterRevoked {
            return Err(SdkError::conflict(
                "class-gallery writer authority was revoked",
            ));
        }
        if state.remote.is_none() {
            let gallery = match self.rebuild(state.database()?) {
                Ok(gallery) => gallery,
                Err(error) => {
                    state.sync_state = CampusSyncState::Quarantined;
                    return Err(error);
                }
            };
            let pending_enrollments = match self.pending_count(state.database()?) {
                Ok(count) => count,
                Err(error) => {
                    state.sync_state = CampusSyncState::Quarantined;
                    return Err(error);
                }
            };
            let report = CampusSyncReport {
                state: CampusSyncState::Ready,
                frames_synced: 0,
                gallery_revision: gallery.gallery_revision(),
                indexed_users: gallery.stats().users,
                pending_enrollments,
            };
            state.sync_state = CampusSyncState::Ready;
            self.inner.gallery.store(Arc::new(gallery));
            return Ok(report);
        }
        let replicated = match self.inner.runtime.block_on(state.database()?.sync()) {
            Ok(replicated) => replicated,
            Err(error) => {
                if is_writer_forbidden(&error.to_string()) {
                    state.sync_state = CampusSyncState::WriterRevoked;
                    return Err(SdkError::conflict(
                        "server revoked class-gallery writer authority",
                    ));
                }
                state.sync_state = CampusSyncState::Offline;
                return Err(SdkError::sync(format!(
                    "synchronize class gallery: {error}"
                )));
            }
        };
        let gallery = match self.rebuild(state.database()?) {
            Ok(gallery) => gallery,
            Err(error) => {
                state.sync_state = CampusSyncState::Quarantined;
                return Err(error);
            }
        };
        let pending_enrollments = match self.pending_count(state.database()?) {
            Ok(count) => count,
            Err(error) => {
                state.sync_state = CampusSyncState::Quarantined;
                return Err(error);
            }
        };
        state.sync_state = CampusSyncState::Ready;
        let report = CampusSyncReport {
            state: state.sync_state,
            frames_synced: replicated.frames_synced(),
            gallery_revision: gallery.gallery_revision(),
            indexed_users: gallery.stats().users,
            pending_enrollments,
        };
        self.inner.gallery.store(Arc::new(gallery));
        Ok(report)
    }

    /// Replace the bearer credential and verify it with an immediate sync.
    ///
    /// The sync URL and device identity remain fixed. A failed rotation restores
    /// the previous credential and leaves the last published matcher available.
    pub fn rotate_auth_token(&self, auth_token: impl Into<String>) -> SdkResult<CampusSyncReport> {
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
            .ok_or_else(|| SdkError::database("class-gallery database is unavailable"))?;
        drop(old_database);

        let provisioning = CampusProvisioning::new(
            self.inner.device_id.clone(),
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
                state.sync_state = CampusSyncState::Quarantined;
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
                state.sync_state = CampusSyncState::Quarantined;
                return Err(error);
            }
        };
        state.database = Some(candidate);
        state.remote = Some(RemoteConfiguration {
            sync_url: provisioning.sync_url,
            auth_token,
        });
        state.sync_state = CampusSyncState::Ready;
        let report = CampusSyncReport {
            state: state.sync_state,
            frames_synced: replicated.frames_synced(),
            gallery_revision: gallery.gallery_revision(),
            indexed_users: gallery.stats().users,
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

    /// Start this writer's only active group-enrollment batch.
    pub fn start_enrollment_batch(&self) -> SdkResult<EnrollmentBatch> {
        let _operation = self.operation()?;
        let state = self.database()?;
        ensure_enrollment_state(state.sync_state)?;
        let batch = EnrollmentBatch {
            id: Uuid::now_v7().to_string(),
            device_id: self.inner.device_id.clone(),
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
            &self.inner.device_id,
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
            &self.inner.device_id,
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
            &self.inner.device_id,
            "cancelled",
        ))
    }

    /// Extract, validate, and durably queue one student's enrollment.
    pub fn enroll_student<I, R>(
        &self,
        student_id: impl Into<String>,
        captures: I,
        batch_id: Option<&str>,
    ) -> SdkResult<CampusEnrollmentResult>
    where
        I: IntoIterator<Item = R>,
        R: AsRef<[u8]>,
    {
        let student_id = student_id.into();
        validate_identifier("student_id", &student_id)?;
        if let Some(batch_id) = batch_id {
            validate_identifier("batch_id", batch_id)?;
        }
        let _operation = self.operation()?;
        {
            let state = self.database()?;
            ensure_enrollment_state(state.sync_state)?;
            self.inner
                .runtime
                .block_on(ensure_active_roster_member(state.database()?, &student_id))?;
        }
        let current = self.inner.gallery.load_full();
        let (artifact, report) = prepare_enrollment(
            current.gallery_id(),
            &student_id,
            captures,
            &current.templates(),
            self.inner.enrollment,
            self.inner.extractor,
            self.inner.limits,
        )?;
        let Some(artifact) = artifact else {
            return Ok(CampusEnrollmentResult {
                submission_id: None,
                batch_id: batch_id.map(str::to_owned),
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
                batch_id,
                device_id: &self.inner.device_id,
                student_id: &student_id,
                gallery_revision: current.gallery_revision(),
                payload: &payload,
                checksum: &checksum,
            },
        ))?;
        let gallery = self.rebuild(state.database()?)?;
        self.inner.gallery.store(Arc::new(gallery));
        Ok(CampusEnrollmentResult {
            submission_id: Some(submission_id),
            batch_id: batch_id.map(str::to_owned),
            report,
        })
    }

    fn rebuild(&self, database: &Database) -> SdkResult<GalleryIndex> {
        let rows = self.inner.runtime.block_on(load_gallery_rows(
            database,
            &self.inner.device_id,
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
            &self.inner.device_id,
        ))
    }

    fn restore_database(
        &self,
        state: &mut DatabaseState,
        remote: RemoteConfiguration,
    ) -> SdkResult<()> {
        let provisioning = CampusProvisioning::new(
            self.inner.device_id.clone(),
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
            .map_err(|_| SdkError::database("class-gallery database lock is poisoned"))
    }

    fn operation(&self) -> SdkResult<MutexGuard<'_, ()>> {
        self.inner
            .operations
            .lock()
            .map_err(|_| SdkError::database("class-gallery operation lock is poisoned"))
    }
}

async fn open_synced_database(
    path: &Path,
    provisioning: &CampusProvisioning,
    existing_replica: bool,
) -> SdkResult<(Database, CampusSyncState)> {
    let database = build_synced_database(path, provisioning).await?;
    match database.sync().await {
        Ok(_) => Ok((database, CampusSyncState::Ready)),
        Err(error) if existing_replica => Ok((database, classify_open_sync_error(&error))),
        Err(error) => Err(SdkError::sync(format!(
            "first class-gallery bootstrap failed: {error}"
        ))),
    }
}

async fn build_synced_database(
    path: &Path,
    provisioning: &CampusProvisioning,
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

fn classify_open_sync_error(error: &libsql::Error) -> CampusSyncState {
    if is_writer_forbidden(&error.to_string()) {
        CampusSyncState::WriterRevoked
    } else {
        CampusSyncState::Offline
    }
}

async fn open_local_database(
    path: &Path,
    gallery_id: &str,
    device_id: &str,
) -> SdkResult<Database> {
    let existed = path.exists();
    let database = Builder::new_local(path)
        .build()
        .await
        .map_err(|error| SdkError::database(format!("open local gallery: {error}")))?;
    if !existed {
        initialize_local_schema(&database, gallery_id, device_id).await?;
    }
    Ok(database)
}

async fn initialize_local_schema(
    database: &Database,
    gallery_id: &str,
    device_id: &str,
) -> SdkResult<()> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect local gallery: {error}")))?;
    connection
        .execute_batch(LOCAL_CLASS_GALLERY_SCHEMA)
        .await
        .map_err(|error| SdkError::database(format!("create local gallery schema: {error}")))?;
    let stream_id = format!("gallery:{gallery_id}");
    for (key, value) in [
        ("schema", CLASS_GALLERY_SCHEMA),
        ("schema_version", CLASS_GALLERY_SCHEMA_VERSION),
        ("stream_id", stream_id.as_str()),
        ("gallery_id", gallery_id),
        ("gallery_revision", "0"),
        ("local_device_id", device_id),
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
    device_id: &str,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<GalleryRows> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect class gallery: {error}")))?;
    connection
        .execute("PRAGMA foreign_keys = ON", ())
        .await
        .map_err(|error| SdkError::database(format!("enable gallery foreign keys: {error}")))?;
    let metadata = query_metadata(&connection).await?;
    let schema = metadata_value(&metadata, "schema")?;
    if schema != CLASS_GALLERY_SCHEMA {
        return Err(SdkError::schema_unsupported(format!(
            "expected schema {CLASS_GALLERY_SCHEMA}, found {schema}"
        )));
    }
    let version = metadata_value(&metadata, "schema_version")?;
    if version != CLASS_GALLERY_SCHEMA_VERSION {
        return Err(SdkError::schema_unsupported(format!(
            "class-gallery schema {version} is not supported; expected {CLASS_GALLERY_SCHEMA_VERSION}"
        )));
    }
    let gallery_id = metadata_value(&metadata, "gallery_id")?.to_owned();
    validate_identifier("gallery_id", &gallery_id)?;
    let gallery_revision = metadata_value(&metadata, "gallery_revision")?
        .parse::<u64>()
        .map_err(|_| SdkError::integrity("gallery_revision is not an unsigned integer"))?;
    let templates = load_template_store(&connection, device_id, extractor, limits).await?;
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
    device_id: &str,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<TemplateStore> {
    let mut store = TemplateStore::new();
    let mut canonical = connection
        .query(
            r#"SELECT t.student_id, t.sdk_format_version, t.extractor_profile,
                    t.template_payload, t.payload_sha256
               FROM gallery_templates t
               JOIN roster_members r ON r.student_id = t.student_id
              WHERE t.modality = 'fingerprint' AND r.effective_to IS NULL
              ORDER BY t.student_id"#,
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
            r#"SELECT s.student_id, s.sdk_format_version, s.extractor_profile,
                    s.candidate_payload, s.payload_sha256
               FROM enrollment_submissions s
               LEFT JOIN enrollment_results result ON result.submission_id = s.id
               JOIN roster_members roster ON roster.student_id = s.student_id
              WHERE s.modality = 'fingerprint'
                AND s.device_id = ?1
                AND result.submission_id IS NULL
                AND roster.effective_to IS NULL
              ORDER BY s.created_at, s.id"#,
            params![device_id],
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
    let student_id = row
        .get::<String>(0)
        .map_err(|error| SdkError::database(format!("decode template student: {error}")))?;
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
    decode_student_template_artifact(
        TemplateArtifactRef {
            student_id: &student_id,
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
    if query_active_enrollment_batch(database, &batch.device_id)
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
                id, device_id, status, started_at, closed_at
             ) VALUES (?1, ?2, 'active', ?3, NULL)"#,
            params![
                batch.id.as_str(),
                batch.device_id.as_str(),
                batch.started_at.as_str()
            ],
        )
        .await
        .map_err(|error| SdkError::database(format!("insert enrollment batch: {error}")))?;
    Ok(())
}

async fn query_active_enrollment_batch(
    database: &Database,
    device_id: &str,
) -> SdkResult<Option<EnrollmentBatch>> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect enrollment batch query: {error}")))?;
    let mut rows = connection
        .query(
            r#"SELECT id, device_id, status, started_at, closed_at
               FROM enrollment_batches
              WHERE device_id = ?1 AND status = 'active'
              LIMIT 1"#,
            params![device_id],
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
        device_id: row
            .get(1)
            .map_err(|error| SdkError::database(format!("decode batch device: {error}")))?,
        status: row
            .get(2)
            .map_err(|error| SdkError::database(format!("decode batch status: {error}")))?,
        started_at: row
            .get(3)
            .map_err(|error| SdkError::database(format!("decode batch start: {error}")))?,
        closed_at: row
            .get(4)
            .map_err(|error| SdkError::database(format!("decode batch close: {error}")))?,
    }))
}

async fn close_enrollment_batch(
    database: &Database,
    batch_id: &str,
    device_id: &str,
    status: &str,
) -> SdkResult<EnrollmentBatch> {
    let active = query_active_enrollment_batch(database, device_id)
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
              WHERE id = ?1 AND device_id = ?4 AND status = 'active'"#,
            params![batch_id, status, closed_at.as_str(), device_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("close enrollment batch: {error}")))?;
    if changed != 1 {
        return Err(SdkError::not_found("active enrollment batch was not found"));
    }
    Ok(EnrollmentBatch {
        id: batch_id.to_owned(),
        device_id: device_id.to_owned(),
        status: status.to_owned(),
        started_at: active.started_at,
        closed_at: Some(closed_at),
    })
}

struct EnrollmentSubmissionRow<'a> {
    id: &'a str,
    batch_id: Option<&'a str>,
    device_id: &'a str,
    student_id: &'a str,
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
    let transaction = connection
        .transaction_with_behavior(TransactionBehavior::Immediate)
        .await
        .map_err(|error| SdkError::database(format!("begin enrollment submission: {error}")))?;
    let mut roster = transaction
        .query(
            r#"SELECT count(*)
               FROM roster_members
              WHERE student_id = ?1 AND effective_to IS NULL"#,
            params![submission.student_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("query enrollment roster: {error}")))?;
    let row = roster
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read enrollment roster: {error}")))?
        .ok_or_else(|| SdkError::integrity("roster count query returned no row"))?;
    let roster_count = row
        .get::<i64>(0)
        .map_err(|error| SdkError::database(format!("decode enrollment roster: {error}")))?;
    drop(roster);
    if roster_count != 1 {
        return Err(SdkError::conflict(
            "student is not an active member of this class gallery",
        ));
    }
    if let Some(batch_id) = submission.batch_id {
        let mut batches = transaction
            .query(
                r#"SELECT count(*) FROM enrollment_batches
                  WHERE id = ?1 AND device_id = ?2 AND status = 'active'"#,
                params![batch_id, submission.device_id],
            )
            .await
            .map_err(|error| SdkError::database(format!("query enrollment batch: {error}")))?;
        let row = batches
            .next()
            .await
            .map_err(|error| SdkError::database(format!("read enrollment batch: {error}")))?
            .ok_or_else(|| SdkError::integrity("batch count query returned no row"))?;
        let batch_count = row
            .get::<i64>(0)
            .map_err(|error| SdkError::database(format!("decode enrollment batch: {error}")))?;
        drop(batches);
        if batch_count != 1 {
            return Err(SdkError::conflict("enrollment batch is not active"));
        }
    }
    let gallery_revision = i64::try_from(submission.gallery_revision)
        .map_err(|_| SdkError::resource_limit("gallery revision exceeds SQLite INTEGER"))?;
    let now = now_text();
    transaction
        .execute(
            r#"INSERT INTO enrollment_submissions (
                id, batch_id, device_id, student_id, modality,
                observed_gallery_revision, sdk_format_version, extractor_profile,
                candidate_payload, payload_sha256, captured_at, created_at
             ) VALUES (?1, ?2, ?3, ?4, 'fingerprint', ?5, ?6, ?7, ?8, ?9, ?10, ?10)"#,
            params![
                submission.id,
                submission.batch_id,
                submission.device_id,
                submission.student_id,
                gallery_revision,
                TEMPLATE_FORMAT_VERSION,
                DEFAULT_EXTRACTOR_PROFILE,
                submission.payload,
                submission.checksum,
                now.as_str(),
            ],
        )
        .await
        .map_err(|error| SdkError::database(format!("insert enrollment submission: {error}")))?;
    transaction
        .commit()
        .await
        .map_err(|error| SdkError::database(format!("commit enrollment submission: {error}")))
}

async fn ensure_active_roster_member(database: &Database, student_id: &str) -> SdkResult<()> {
    let connection = database
        .connect()
        .map_err(|error| SdkError::database(format!("connect enrollment roster: {error}")))?;
    let mut rows = connection
        .query(
            r#"SELECT count(*)
                 FROM roster_members
                WHERE student_id = ?1 AND effective_to IS NULL"#,
            params![student_id],
        )
        .await
        .map_err(|error| SdkError::database(format!("query enrollment roster: {error}")))?;
    let row = rows
        .next()
        .await
        .map_err(|error| SdkError::database(format!("read enrollment roster: {error}")))?
        .ok_or_else(|| SdkError::integrity("roster count query returned no row"))?;
    let count = row
        .get::<i64>(0)
        .map_err(|error| SdkError::database(format!("decode enrollment roster: {error}")))?;
    if count != 1 {
        return Err(SdkError::conflict(
            "student is not an active member of this class gallery",
        ));
    }
    Ok(())
}

async fn query_pending_enrollment_count(database: &Database, device_id: &str) -> SdkResult<usize> {
    let connection = database.connect().map_err(|error| {
        SdkError::database(format!("connect pending enrollment count: {error}"))
    })?;
    let mut rows = connection
        .query(
            r#"SELECT count(*)
                 FROM enrollment_submissions submission
                 LEFT JOIN enrollment_results result
                   ON result.submission_id = submission.id
                WHERE submission.device_id = ?1
                  AND result.submission_id IS NULL"#,
            params![device_id],
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
    student_id: &str,
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
        if accepted.len() >= config.max_templates_per_student {
            attempts.push(rejected_attempt(
                student_id,
                None,
                EnrollmentRejectionReason::MaxTemplatesForStudent {
                    max_templates: config.max_templates_per_student,
                },
            ));
            continue;
        }
        let record_id = Uuid::now_v7().to_string();
        let template = match extract_raw_bytes(
            record_id.clone(),
            student_id.to_owned(),
            raw.as_ref(),
            extractor,
        ) {
            Ok(template) => template,
            Err(error) => {
                attempts.push(rejected_attempt(
                    student_id,
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
                student_id,
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
                student_id,
                Some(template.quality),
                EnrollmentRejectionReason::DuplicateOfOtherStudent { duplicate },
            ));
            duplicate_found = true;
            break;
        }
        attempts.push(EnrollmentAttempt {
            student_id: student_id.to_owned(),
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
        accepted_students: usize::from(!accepted.is_empty()),
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
            student_id: hit.user_id,
            record_id: hit.record_id,
            score: hit.score,
            verification_score: hit.verification_score,
        }))
}

fn rejected_attempt(
    student_id: &str,
    quality: Option<u8>,
    rejection: EnrollmentRejectionReason,
) -> EnrollmentAttempt {
    EnrollmentAttempt {
        student_id: student_id.to_owned(),
        record_id: None,
        quality,
        accepted: false,
        rejection: Some(rejection),
    }
}

fn now_text() -> String {
    Utc::now().to_rfc3339_opts(SecondsFormat::Millis, true)
}

fn is_writer_forbidden(message: &str) -> bool {
    let message = message.to_ascii_lowercase();
    message.contains("gallery_writer_forbidden")
        || message.contains("writer forbidden")
        || message.contains("status 403")
        || message.contains("status: 403")
}

fn ensure_enrollment_state(state: CampusSyncState) -> SdkResult<()> {
    match state {
        CampusSyncState::Ready | CampusSyncState::Offline => Ok(()),
        CampusSyncState::WriterRevoked => Err(SdkError::conflict(
            "class-gallery writer authority was revoked",
        )),
        CampusSyncState::Quarantined => Err(SdkError::integrity(
            "class-gallery state is quarantined pending a valid synchronization",
        )),
    }
}

const LOCAL_CLASS_GALLERY_SCHEMA: &str = r#"
PRAGMA page_size = 4096;
PRAGMA foreign_keys = ON;
CREATE TABLE sync_metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
) STRICT;
CREATE TABLE roster_members (
    student_id TEXT PRIMARY KEY,
    enrollment_id TEXT NOT NULL,
    effective_from TEXT NOT NULL,
    effective_to TEXT,
    profile_revision INTEGER CHECK (profile_revision IS NULL OR profile_revision > 0),
    updated_at TEXT NOT NULL
) STRICT;
CREATE TABLE gallery_templates (
    student_id TEXT NOT NULL,
    modality TEXT NOT NULL CHECK (modality IN ('fingerprint', 'face')),
    profile_revision INTEGER NOT NULL CHECK (profile_revision > 0),
    sdk_format_version TEXT NOT NULL,
    extractor_profile TEXT NOT NULL,
    template_payload BLOB NOT NULL CHECK (length(template_payload) > 0),
    payload_sha256 TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (student_id, modality),
    FOREIGN KEY (student_id) REFERENCES roster_members(student_id) ON DELETE CASCADE
) STRICT;
CREATE TABLE enrollment_batches (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('active', 'closed', 'cancelled')),
    started_at TEXT NOT NULL,
    closed_at TEXT,
    CHECK (
        (status = 'active' AND closed_at IS NULL)
        OR (status IN ('closed', 'cancelled') AND closed_at IS NOT NULL)
    )
) STRICT;
CREATE UNIQUE INDEX enrollment_batches_one_active_idx
    ON enrollment_batches (device_id) WHERE status = 'active';
CREATE TABLE enrollment_submissions (
    id TEXT PRIMARY KEY,
    batch_id TEXT,
    device_id TEXT NOT NULL,
    student_id TEXT NOT NULL,
    modality TEXT NOT NULL CHECK (modality IN ('fingerprint', 'face')),
    observed_gallery_revision INTEGER NOT NULL CHECK (observed_gallery_revision >= 0),
    sdk_format_version TEXT NOT NULL,
    extractor_profile TEXT NOT NULL,
    candidate_payload BLOB NOT NULL CHECK (length(candidate_payload) > 0),
    payload_sha256 TEXT NOT NULL,
    captured_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES enrollment_batches(id)
) STRICT;
CREATE INDEX enrollment_submissions_student_idx
    ON enrollment_submissions (student_id, created_at, id);
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
        std::env::temp_dir().join(format!("biometric-campus-test-{}", Uuid::now_v7()))
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

    fn local_sdk(root: &Path) -> CampusBiometricSdk {
        local_sdk_for_device(root, "device-1")
    }

    fn local_sdk_for_device(root: &Path, device_id: &str) -> CampusBiometricSdk {
        CampusBiometricSdk::open_local_with_config(
            root.to_path_buf(),
            "class:school:session:a".to_owned(),
            device_id.to_owned(),
            EnrollmentConfig::default().with_min_quality(0),
            ExtractorConfig::default(),
            IdentifyConfig::default(),
            SdkLimits::default(),
        )
        .unwrap()
    }

    fn add_roster_member(sdk: &CampusBiometricSdk, student_id: &str) {
        let state = sdk.database().unwrap();
        let connection = state.database().unwrap().connect().unwrap();
        let now = now_text();
        sdk.inner
            .runtime
            .block_on(connection.execute(
                r#"INSERT INTO roster_members (
                    student_id, enrollment_id, effective_from, effective_to, updated_at
                 ) VALUES (?1, ?2, ?3, NULL, ?3)"#,
                params![student_id, format!("enrollment-{student_id}"), now],
            ))
            .unwrap();
    }

    fn corrupt_submission_checksum(sdk: &CampusBiometricSdk, submission_id: &str) {
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

    #[test]
    fn group_enrollment_is_durable_and_provisional_after_restart() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        add_roster_member(&sdk, "student-1");
        let batch = sdk.start_enrollment_batch().unwrap();
        let result = sdk
            .enroll_student("student-1", [ridge_pattern(3)], Some(&batch.id))
            .unwrap();
        assert!(result.submission_id.is_some());
        assert_eq!(sdk.gallery_stats().users, 1);
        assert_eq!(sdk.pending_enrollment_count().unwrap(), 1);
        drop(sdk);

        let reopened = local_sdk(&root);
        assert_eq!(reopened.gallery_stats().users, 1);
        assert_eq!(
            reopened.active_enrollment_batch().unwrap().unwrap().id,
            batch.id
        );
        drop(reopened);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn enrollment_requires_current_roster_membership() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        let error = sdk
            .enroll_student("student-1", [ridge_pattern(4)], None)
            .unwrap_err();
        assert_eq!(error.code(), super::super::error::SdkErrorCode::Conflict);
        drop(sdk);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn only_one_group_batch_can_be_active() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        let batch = sdk.start_enrollment_batch().unwrap();
        assert_eq!(
            sdk.start_enrollment_batch().unwrap_err().code(),
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
        let root = temporary_root();
        let original = local_sdk_for_device(&root, "device-1");
        let original_batch = original.start_enrollment_batch().unwrap();
        drop(original);

        let replacement = local_sdk_for_device(&root, "device-2");
        assert!(replacement.active_enrollment_batch().unwrap().is_none());
        let replacement_batch = replacement.start_enrollment_batch().unwrap();
        assert_eq!(replacement_batch.device_id, "device-2");
        drop(replacement);

        let reopened_original = local_sdk_for_device(&root, "device-1");
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
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn invalid_synchronized_rows_quarantine_enrollment_but_preserve_matcher() {
        let root = temporary_root();
        let sdk = local_sdk(&root);
        add_roster_member(&sdk, "student-1");
        add_roster_member(&sdk, "student-2");
        let enrollment = sdk
            .enroll_student("student-1", [ridge_pattern(5)], None)
            .unwrap();
        let submission_id = enrollment.submission_id.unwrap();
        assert_eq!(sdk.gallery_stats().users, 1);

        corrupt_submission_checksum(&sdk, &submission_id);
        assert_eq!(
            sdk.sync().unwrap_err().code(),
            super::super::error::SdkErrorCode::Integrity
        );
        assert_eq!(sdk.sync_state().unwrap(), CampusSyncState::Quarantined);
        assert_eq!(sdk.gallery_stats().users, 1);
        assert_eq!(
            sdk.enroll_student("student-2", [ridge_pattern(6)], None)
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
