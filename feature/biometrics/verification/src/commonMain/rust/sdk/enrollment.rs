//! Resumable enrollment and crash-recoverable filesystem facade.

use std::collections::{BTreeSet, HashMap, HashSet};
use std::fs::{self, File, OpenOptions};
use std::io::{Cursor, Read, Write};
use std::path::{Path, PathBuf};

use fs2::FileExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::bundle::{DeltaApplyStatus, IndexDelta, LocationIndexBundle, TemplateStore};
use super::error::{SdkError, SdkResult};
use super::extractor::{ExtractedTemplate, ExtractorConfig, extract_raw_bytes};
use super::index::{BiometricIndex, IdentifyConfig, RerankConfig, SearchConfig};
use super::limits::SdkLimits;
use super::storage::{
    atomic_write_bytes, location_storage_key, new_operation_id, validate_identifier,
};

const SESSION_MAGIC: &[u8; 8] = b"BMSENR\0\0";
const SESSION_HASH_LEN: usize = 32;
/// Balanced default for enrollment quality. The score remains configurable.
pub const DEFAULT_ENROLLMENT_MIN_QUALITY: u8 = 65;

/// SDK initialization settings.
#[derive(Debug, Clone)]
pub struct SdkConfig {
    /// App-owned writable storage root.
    pub storage_root: PathBuf,
    /// Enrollment policy.
    pub enrollment: EnrollmentConfig,
    /// Extractor profile persisted into new bundles.
    pub extractor: ExtractorConfig,
    /// Identify policy persisted into new bundles.
    pub identify: IdentifyConfig,
    /// Resource limits for files and location indexes.
    pub limits: SdkLimits,
}

impl SdkConfig {
    /// Construct settings using current defaults.
    pub fn new(storage_root: impl Into<PathBuf>) -> Self {
        Self {
            storage_root: storage_root.into(),
            enrollment: EnrollmentConfig::default(),
            extractor: ExtractorConfig::default(),
            identify: IdentifyConfig::default(),
            limits: SdkLimits::default(),
        }
    }

    /// Replace enrollment policy.
    pub fn with_enrollment_config(mut self, enrollment: EnrollmentConfig) -> Self {
        self.enrollment = enrollment;
        self
    }

    /// Set minimum enrollment quality.
    pub fn with_enrollment_min_quality(mut self, min_quality: u8) -> Self {
        self.enrollment.min_quality = min_quality;
        self
    }

    /// Replace the extractor profile for newly created bundles.
    pub fn with_extractor_config(mut self, extractor: ExtractorConfig) -> Self {
        self.extractor = extractor;
        self
    }

    /// Replace the default identify policy for newly created bundles.
    pub fn with_identify_config(mut self, identify: IdentifyConfig) -> Self {
        self.identify = identify;
        self
    }

    /// Replace filesystem and decoding limits.
    pub fn with_limits(mut self, limits: SdkLimits) -> Self {
        self.limits = limits;
        self
    }
}

/// Filesystem-backed SDK facade.
#[derive(Debug, Clone)]
pub struct BiometricSdk {
    storage_root: PathBuf,
    enrollment: EnrollmentConfig,
    extractor: ExtractorConfig,
    identify: IdentifyConfig,
    limits: SdkLimits,
}

/// Enrollment acceptance and duplicate policy.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EnrollmentConfig {
    /// Minimum quality accepted for a template.
    pub min_quality: u8,
    /// Maximum accepted templates retained for one user.
    pub max_templates_per_user: usize,
    /// Cross-user duplicate prevention settings.
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
            max_templates_per_user: 2,
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
    /// Set minimum enrollment quality.
    pub fn with_min_quality(mut self, min_quality: u8) -> Self {
        self.min_quality = min_quality;
        self
    }

    fn validate(self, limits: SdkLimits) -> SdkResult<Self> {
        if self.min_quality > 100 {
            return Err(SdkError::invalid_input("min_quality must be in 0..=100"));
        }
        if self.max_templates_per_user == 0
            || self.max_templates_per_user > limits.max_records.min(16)
        {
            return Err(SdkError::invalid_input(
                "max_templates_per_user must be in 1..=16",
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

/// Cross-user duplicate prevention settings.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DuplicateCheckConfig {
    /// Enable duplicate prevention.
    pub enabled: bool,
    /// Minimum final score considered a duplicate.
    pub min_score: f32,
    /// Minimum geometric score considered a duplicate.
    pub min_verification_score: f32,
    /// Candidate-search settings.
    pub search: SearchConfig,
}

/// Result of one enrollment capture.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct EnrollmentAttempt {
    /// Requested user identifier.
    pub user_id: String,
    /// SDK-generated record id when committed.
    pub record_id: Option<String>,
    /// Extracted quality when extraction succeeded.
    pub quality: Option<u8>,
    /// Whether this capture was committed.
    pub accepted: bool,
    /// Rejection details.
    pub rejection: Option<EnrollmentRejectionReason>,
}

/// Enrollment rejection reason.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case", deny_unknown_fields)]
pub enum EnrollmentRejectionReason {
    /// Capture bytes or extractor input were invalid.
    InvalidCapture {
        /// Diagnostic extraction or input-validation message.
        message: String,
    },
    /// Quality was below policy.
    LowQuality {
        /// Measured capture quality.
        quality: u8,
        /// Minimum configured enrollment quality.
        min_quality: u8,
    },
    /// User already reached the configured template count.
    MaxTemplatesForUser {
        /// Maximum templates retained for one user.
        max_templates: usize,
    },
    /// Capture matched a different enrolled user.
    DuplicateOfOtherUser {
        /// Existing enrollment that matched this capture.
        duplicate: DuplicateEnrollmentMatch,
    },
    /// A previously acceptable capture was not committed because the batch failed.
    NotCommitted {
        /// Diagnostic reason the accepted capture was rolled back.
        message: String,
    },
}

/// Existing enrollment matched during duplicate prevention.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DuplicateEnrollmentMatch {
    /// Existing user id.
    pub user_id: String,
    /// Existing finger record id.
    pub record_id: String,
    /// Final score.
    pub score: f32,
    /// Geometric score.
    pub verification_score: f32,
}

/// Enrollment operation report.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct EnrollmentReport {
    /// Location id.
    pub location_id: String,
    /// Committed finger records represented by this report.
    pub accepted_records: usize,
    /// Distinct users represented by committed records.
    pub accepted_users: usize,
    /// Rejected or non-committed captures.
    pub rejected_captures: usize,
    /// Capture-level outcomes.
    pub attempts: Vec<EnrollmentAttempt>,
}

/// Lightweight active-session summary.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct EnrollmentSessionSummary {
    /// Location id.
    pub location_id: String,
    /// Committed draft records.
    pub accepted_records: usize,
    /// Distinct draft users.
    pub accepted_users: usize,
    /// Rejected attempts.
    pub rejected_captures: usize,
}

/// Result of closing initial enrollment.
#[derive(Debug)]
pub struct EnrollmentCloseResult {
    /// Produced location bundle.
    pub bundle: LocationIndexBundle,
    /// Final report.
    pub report: EnrollmentReport,
    /// Persisted bundle path.
    pub bundle_path: PathBuf,
}

/// Result of replacing a user's enrollment in an existing bundle.
#[derive(Debug)]
pub struct EnrollmentDeltaResult {
    /// Capture-level report.
    pub report: EnrollmentReport,
    /// Applied sync delta when at least one capture was accepted.
    pub delta: Option<IndexDelta>,
}

/// Exclusive handle to the one active initial enrollment session.
#[derive(Debug)]
pub struct EnrollmentSession {
    storage_root: PathBuf,
    state: EnrollmentSessionState,
    limits: SdkLimits,
    _lock_file: File,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct EnrollmentSessionState {
    revision: u64,
    location_id: String,
    templates: Vec<ExtractedTemplate>,
    attempts: Vec<EnrollmentAttempt>,
    enrollment: EnrollmentConfig,
    extractor: ExtractorConfig,
    identify: IdentifyConfig,
}

impl BiometricSdk {
    /// Open the SDK and prepare its owned directory structure.
    pub fn open(config: SdkConfig) -> SdkResult<Self> {
        let limits = config.limits.validate()?;
        let enrollment = config.enrollment.validate(limits)?;
        let extractor = config.extractor.validate(limits)?;
        let identify = config.identify.normalized();
        for directory in ["enrollment", "bundles", "indexes", "deltas", "locks", "tmp"] {
            let path = config.storage_root.join(directory);
            fs::create_dir_all(&path).map_err(|error| {
                SdkError::io(format!("create SDK directory {}", path.display()), error)
            })?;
        }
        Ok(Self {
            storage_root: config.storage_root,
            enrollment,
            extractor,
            identify,
            limits,
        })
    }

    /// Enrollment policy used for new captures.
    pub fn enrollment_config(&self) -> EnrollmentConfig {
        self.enrollment
    }

    /// Whether a resumable initial enrollment file exists.
    pub fn has_active_enrollment_session(&self) -> SdkResult<bool> {
        self.session_path()
            .try_exists()
            .map_err(|error| SdkError::io("check enrollment session", error))
    }

    /// Start the only initial enrollment session.
    pub fn start_enrollment_session(
        &self,
        location_id: impl Into<String>,
    ) -> SdkResult<EnrollmentSession> {
        let lock_file = self.acquire_session_lock()?;
        if self.has_active_enrollment_session()? {
            return Err(SdkError::conflict(
                "an enrollment draft already exists; resume or discard it",
            ));
        }
        let location_id = location_id.into();
        validate_identifier("location_id", &location_id)?;
        if self
            .bundle_path(&location_id)
            .try_exists()
            .map_err(|error| SdkError::io("check existing location bundle", error))?
        {
            return Err(SdkError::conflict(
                "initial enrollment cannot replace an existing location bundle",
            ));
        }
        let state = EnrollmentSessionState {
            revision: 0,
            location_id,
            templates: Vec::new(),
            attempts: Vec::new(),
            enrollment: self.enrollment,
            extractor: self.extractor,
            identify: self.identify,
        };
        let mut session = EnrollmentSession {
            storage_root: self.storage_root.clone(),
            state,
            limits: self.limits,
            _lock_file: lock_file,
        };
        session.persist()?;
        Ok(session)
    }

    /// Resume the draft while acquiring exclusive ownership.
    pub fn resume_enrollment_session(&self) -> SdkResult<EnrollmentSession> {
        let lock_file = self.acquire_session_lock()?;
        let path = self.session_path();
        let metadata = fs::metadata(&path).map_err(|error| {
            if error.kind() == std::io::ErrorKind::NotFound {
                SdkError::not_found("no enrollment session can be resumed")
            } else {
                SdkError::io(format!("read session metadata {}", path.display()), error)
            }
        })?;
        if metadata.len() > self.limits.max_bundle_bytes as u64 {
            return Err(SdkError::resource_limit(
                "enrollment session file exceeds limit",
            ));
        }
        let bytes = fs::read(&path)
            .map_err(|error| SdkError::io(format!("read session {}", path.display()), error))?;
        let state = decode_session_state(&bytes, self.limits)?;
        validate_session_state(&state, self.limits)?;
        Ok(EnrollmentSession {
            storage_root: self.storage_root.clone(),
            state,
            limits: self.limits,
            _lock_file: lock_file,
        })
    }

    /// Discard a draft only when no other owner holds the lease.
    pub fn discard_enrollment_session(&self) -> SdkResult<()> {
        let _lock = self.acquire_session_lock()?;
        let path = self.session_path();
        if path
            .try_exists()
            .map_err(|error| SdkError::io("check enrollment session", error))?
        {
            fs::remove_file(&path).map_err(|error| {
                SdkError::io(format!("remove session {}", path.display()), error)
            })?;
            sync_directory(path.parent().unwrap_or(&self.storage_root))?;
        }
        Ok(())
    }

    /// Load a bundle and replay any durable delta written before an interrupted save.
    pub fn load_bundle(&self, location_id: &str) -> SdkResult<LocationIndexBundle> {
        validate_identifier("location_id", location_id)?;
        let _lock = self.acquire_location_lock(location_id)?;
        self.load_bundle_unlocked(location_id, true)
    }

    /// Save a validated bundle under its collision-free location key.
    pub fn save_bundle(&self, bundle: &LocationIndexBundle) -> SdkResult<PathBuf> {
        let location_id = bundle.manifest().location_id();
        validate_identifier("location_id", location_id)?;
        let _lock = self.acquire_location_lock(location_id)?;
        self.save_bundle_unlocked(bundle)
    }

    /// Import and validate a snapshot from a platform-provided stream.
    ///
    /// The expected location prevents an otherwise valid snapshot from being
    /// installed under the wrong school's storage key. Existing state cannot be
    /// rolled back or silently replaced by a divergent snapshot at the same
    /// generation.
    pub fn import_bundle(
        &self,
        expected_location_id: &str,
        reader: impl Read,
    ) -> SdkResult<PathBuf> {
        validate_identifier("location_id", expected_location_id)?;
        let _lock = self.acquire_location_lock(expected_location_id)?;
        let bundle = LocationIndexBundle::read_bundle_with_limits(reader, self.limits)?;
        if bundle.manifest().location_id() != expected_location_id {
            return Err(SdkError::conflict(format!(
                "bundle location mismatch: expected={expected_location_id}, received={}",
                bundle.manifest().location_id()
            )));
        }
        self.save_bundle_unlocked(&bundle)
    }

    /// Validate and stream a recovered snapshot to platform-owned storage.
    pub fn export_bundle(&self, location_id: &str, writer: impl Write) -> SdkResult<()> {
        validate_identifier("location_id", location_id)?;
        let _lock = self.acquire_location_lock(location_id)?;
        let bundle = self.load_bundle_unlocked(location_id, true)?;
        bundle.write_bundle(writer)
    }

    /// Enroll or replace one user and persist delta-before-bundle transactionally.
    pub fn enroll_user<I, R>(
        &self,
        location_id: &str,
        user_id: impl Into<String>,
        captures: I,
    ) -> SdkResult<EnrollmentDeltaResult>
    where
        I: IntoIterator<Item = R>,
        R: AsRef<[u8]>,
    {
        validate_identifier("location_id", location_id)?;
        let _lock = self.acquire_location_lock(location_id)?;
        let mut bundle = self.load_bundle_unlocked(location_id, true)?;
        let result = bundle.enroll_user_with_config(user_id, captures, self.enrollment)?;
        if let Some(delta) = &result.delta {
            /*
            Persist delta before snapshot. A crash between these writes leaves
            the old bundle plus a durable next delta, which load_bundle replays.
            Reversing this order could publish a generation with no recoverable
            operation for peers or the local device.
            */
            self.save_delta(delta)?;
            self.persist_bundle_and_cache(&bundle)?;
        }
        Ok(result)
    }

    /// Remove a user and persist a delta before the updated bundle snapshot.
    pub fn remove_user(&self, location_id: &str, user_id: &str) -> SdkResult<IndexDelta> {
        validate_identifier("location_id", location_id)?;
        validate_identifier("user_id", user_id)?;
        let _lock = self.acquire_location_lock(location_id)?;
        let mut bundle = self.load_bundle_unlocked(location_id, true)?;
        let delta = bundle.remove_user(user_id)?;
        self.save_delta(&delta)?;
        self.persist_bundle_and_cache(&bundle)?;
        Ok(delta)
    }

    /// Apply and durably persist one downloaded delta.
    pub fn apply_delta(
        &self,
        location_id: &str,
        delta: &IndexDelta,
    ) -> SdkResult<DeltaApplyStatus> {
        validate_identifier("location_id", location_id)?;
        let _lock = self.acquire_location_lock(location_id)?;
        let mut bundle = self.load_bundle_unlocked(location_id, true)?;
        let status = bundle.apply_delta(delta)?;
        self.save_delta(delta)?;
        if status == DeltaApplyStatus::Applied {
            self.persist_bundle_and_cache(&bundle)?;
        }
        Ok(status)
    }

    /// Collision-free stored bundle path for a location.
    pub fn bundle_path(&self, location_id: &str) -> PathBuf {
        self.storage_root
            .join("bundles")
            .join(format!("{}.biobundle", location_storage_key(location_id)))
    }

    fn index_cache_path(&self, location_id: &str) -> PathBuf {
        index_cache_path(&self.storage_root, location_id)
    }

    pub(crate) fn storage_root(&self) -> &Path {
        &self.storage_root
    }

    pub(crate) fn limits(&self) -> SdkLimits {
        self.limits
    }

    pub(crate) fn validate_location_id(location_id: &str) -> SdkResult<()> {
        validate_identifier("location_id", location_id)
    }

    fn save_bundle_unlocked(&self, bundle: &LocationIndexBundle) -> SdkResult<PathBuf> {
        bundle.validate()?;
        let location_id = bundle.manifest().location_id();
        let path = self.bundle_path(location_id);
        if path
            .try_exists()
            .map_err(|error| SdkError::io("check existing location bundle", error))?
        {
            let current = self.load_bundle_unlocked(location_id, true)?;
            let incoming_generation = bundle.manifest().generation();
            let current_generation = current.manifest().generation();
            if incoming_generation < current_generation {
                return Err(SdkError::conflict(format!(
                    "bundle rollback rejected: current={current_generation}, incoming={incoming_generation}"
                )));
            }
            if incoming_generation == current_generation && current != *bundle {
                return Err(SdkError::conflict(format!(
                    "bundle generation {incoming_generation} has divergent content"
                )));
            }
            if current == *bundle {
                write_index_cache_best_effort(&self.storage_root, bundle);
                return Ok(path);
            }
        }
        self.persist_bundle_and_cache(bundle)?;
        Ok(path)
    }

    fn persist_bundle_and_cache(&self, bundle: &LocationIndexBundle) -> SdkResult<()> {
        bundle.save_bundle(self.bundle_path(bundle.manifest().location_id()))?;
        write_index_cache_best_effort(&self.storage_root, bundle);
        Ok(())
    }

    fn load_bundle_unlocked(
        &self,
        location_id: &str,
        recover: bool,
    ) -> SdkResult<LocationIndexBundle> {
        let path = self.bundle_path(location_id);
        let file = File::open(&path).map_err(|error| {
            if error.kind() == std::io::ErrorKind::NotFound {
                SdkError::not_found(format!("bundle for {location_id} does not exist"))
            } else {
                SdkError::io(format!("open bundle {}", path.display()), error)
            }
        })?;
        let cache = read_index_cache_best_effort(
            &self.index_cache_path(location_id),
            self.limits.max_index_bytes,
        );
        let (mut bundle, cache_hit) =
            LocationIndexBundle::read_bundle_with_index_cache(file, cache.as_deref(), self.limits)?;
        if bundle.manifest().location_id() != location_id {
            return Err(SdkError::integrity(format!(
                "stored bundle location mismatch: requested={location_id}, loaded={}",
                bundle.manifest().location_id()
            )));
        }
        if !cache_hit {
            write_index_cache_best_effort(&self.storage_root, &bundle);
        }
        if recover && self.recover_deltas(&mut bundle)? {
            self.persist_bundle_and_cache(&bundle)?;
        }
        Ok(bundle)
    }

    fn recover_deltas(&self, bundle: &mut LocationIndexBundle) -> SdkResult<bool> {
        let directory = self.delta_directory(bundle.manifest().location_id());
        if !directory
            .try_exists()
            .map_err(|error| SdkError::io("check delta directory", error))?
        {
            return Ok(false);
        }
        let entries = fs::read_dir(&directory).map_err(|error| {
            SdkError::io(
                format!("read delta directory {}", directory.display()),
                error,
            )
        })?;
        let mut paths = Vec::new();
        for entry in entries {
            let path = entry
                .map_err(|error| {
                    SdkError::io(
                        format!("read entry in delta directory {}", directory.display()),
                        error,
                    )
                })?
                .path();
            if path.extension().and_then(|value| value.to_str()) == Some("json") {
                paths.push(path);
            }
        }
        paths.sort();
        let mut changed = false;
        for path in paths {
            let metadata = fs::metadata(&path).map_err(|error| {
                SdkError::io(format!("read delta metadata {}", path.display()), error)
            })?;
            if metadata.len() > self.limits.max_template_bytes as u64 {
                return Err(SdkError::resource_limit("stored delta exceeds byte limit"));
            }
            let bytes = fs::read(&path)
                .map_err(|error| SdkError::io(format!("read delta {}", path.display()), error))?;
            let delta = IndexDelta::from_json_bytes(&bytes)?;
            changed |= bundle.apply_delta(&delta)? == DeltaApplyStatus::Applied;
        }
        Ok(changed)
    }

    fn save_delta(&self, delta: &IndexDelta) -> SdkResult<PathBuf> {
        let directory = self.delta_directory(delta.location_id());
        fs::create_dir_all(&directory).map_err(|error| {
            SdkError::io(
                format!("create delta directory {}", directory.display()),
                error,
            )
        })?;
        let path = directory.join(format!("{:020}.json", delta.sequence()));
        if path
            .try_exists()
            .map_err(|error| SdkError::io("check delta path", error))?
        {
            let existing = fs::read(&path).map_err(|error| {
                SdkError::io(format!("read existing delta {}", path.display()), error)
            })?;
            if IndexDelta::from_json_bytes(&existing)? == *delta {
                return Ok(path);
            }
            return Err(SdkError::conflict(format!(
                "delta sequence {} already has a different payload",
                delta.sequence()
            )));
        }
        let bytes = delta.to_json_bytes()?;
        atomic_write_bytes(&path, &bytes)?;
        Ok(path)
    }

    fn delta_directory(&self, location_id: &str) -> PathBuf {
        self.storage_root
            .join("deltas")
            .join(location_storage_key(location_id))
    }

    fn session_path(&self) -> PathBuf {
        session_path(&self.storage_root)
    }

    fn acquire_session_lock(&self) -> SdkResult<File> {
        acquire_lock(
            &self.storage_root.join("locks/enrollment.lock"),
            "an enrollment session is already active",
            true,
        )
    }

    fn acquire_location_lock(&self, location_id: &str) -> SdkResult<File> {
        acquire_lock(
            &self
                .storage_root
                .join("locks")
                .join(format!("{}.lock", location_storage_key(location_id))),
            "location state is being updated by another SDK handle",
            false,
        )
    }
}

impl EnrollmentSession {
    /// Add one capture and persist the resulting accepted or rejected attempt.
    pub fn add_capture(
        &mut self,
        user_id: impl Into<String>,
        raw: &[u8],
    ) -> SdkResult<EnrollmentAttempt> {
        let user_id = user_id.into();
        validate_identifier("user_id", &user_id)?;
        let (attempt, template) = self.evaluate_capture(&user_id, raw)?;
        let previous_templates = self.state.templates.len();
        let previous_attempts = self.state.attempts.len();
        if let Some(template) = template {
            self.state.templates.push(template);
        }
        self.state.attempts.push(attempt.clone());
        if let Err(error) = self.persist() {
            self.state.templates.truncate(previous_templates);
            self.state.attempts.truncate(previous_attempts);
            return Err(error);
        }
        Ok(attempt)
    }

    /// Remove a user's draft templates and persist atomically.
    pub fn remove_user(&mut self, user_id: &str) -> SdkResult<()> {
        validate_identifier("user_id", user_id)?;
        let previous = self.state.clone();
        self.state
            .templates
            .retain(|template| template.record.user_id != user_id);
        for attempt in &mut self.state.attempts {
            if attempt.user_id == user_id && attempt.accepted {
                attempt.accepted = false;
                attempt.record_id = None;
                attempt.rejection = Some(EnrollmentRejectionReason::NotCommitted {
                    message: "removed from enrollment draft".to_owned(),
                });
            }
        }
        if let Err(error) = self.persist() {
            self.state = previous;
            return Err(error);
        }
        Ok(())
    }

    /// Current draft summary.
    pub fn summary(&self) -> EnrollmentSessionSummary {
        EnrollmentSessionSummary {
            location_id: self.state.location_id.clone(),
            accepted_records: self.state.templates.len(),
            accepted_users: user_count(&self.state.templates),
            rejected_captures: self
                .state
                .attempts
                .iter()
                .filter(|attempt| !attempt.accepted)
                .count(),
        }
    }

    /// Detailed draft report.
    pub fn report(&self) -> EnrollmentReport {
        report_from_state(&self.state)
    }

    /// Close the draft, atomically write the initial bundle, and remove the session file.
    pub fn close(self) -> SdkResult<EnrollmentCloseResult> {
        if self.state.templates.is_empty() {
            return Err(SdkError::conflict(
                "cannot close enrollment without an accepted template",
            ));
        }
        if let Some(duplicate) = final_duplicate_audit(
            &self.state.templates,
            self.state.enrollment.duplicate,
            self.state.extractor,
            self.limits,
        )? {
            return Err(SdkError::conflict(format!(
                "enrollment contains cross-user duplicate {} / {}",
                duplicate.record_id, duplicate.user_id
            )));
        }
        let store = TemplateStore::from_templates(self.state.templates.clone())?;
        let bundle = LocationIndexBundle::build_with_profiles(
            self.state.location_id.clone(),
            store,
            self.state.extractor,
            self.state.identify,
            self.limits,
        )?;
        let _location_lock = acquire_lock(
            &self.storage_root.join("locks").join(format!(
                "{}.lock",
                location_storage_key(&self.state.location_id)
            )),
            "location state is being updated by another SDK handle",
            false,
        )?;
        let bundle_path = bundle_path(&self.storage_root, &self.state.location_id);
        if bundle_path
            .try_exists()
            .map_err(|error| SdkError::io("check initial bundle path", error))?
        {
            let existing = LocationIndexBundle::load_bundle(&bundle_path)?;
            if existing != bundle {
                return Err(SdkError::conflict(
                    "initial enrollment cannot overwrite an existing bundle",
                ));
            }
        } else {
            bundle.save_bundle(&bundle_path)?;
        }
        write_index_cache_best_effort(&self.storage_root, &bundle);
        let session_path = session_path(&self.storage_root);
        if session_path
            .try_exists()
            .map_err(|error| SdkError::io("check session file before close", error))?
        {
            fs::remove_file(&session_path).map_err(|error| {
                SdkError::io(format!("remove session {}", session_path.display()), error)
            })?;
            sync_directory(session_path.parent().unwrap_or(&self.storage_root))?;
        }
        Ok(EnrollmentCloseResult {
            bundle,
            report: report_from_state(&self.state),
            bundle_path,
        })
    }

    fn evaluate_capture(
        &self,
        user_id: &str,
        raw: &[u8],
    ) -> SdkResult<(EnrollmentAttempt, Option<ExtractedTemplate>)> {
        let accepted_for_user = self
            .state
            .templates
            .iter()
            .filter(|template| template.record.user_id == user_id)
            .count();
        if accepted_for_user >= self.state.enrollment.max_templates_per_user {
            return Ok((
                rejected_attempt(
                    user_id,
                    None,
                    EnrollmentRejectionReason::MaxTemplatesForUser {
                        max_templates: self.state.enrollment.max_templates_per_user,
                    },
                ),
                None,
            ));
        }
        let record_id = format!("r{}", new_operation_id());
        let template =
            match extract_raw_bytes(record_id.clone(), user_id, raw, self.state.extractor) {
                Ok(template) => template,
                Err(error) => {
                    return Ok((
                        rejected_attempt(
                            user_id,
                            None,
                            EnrollmentRejectionReason::InvalidCapture {
                                message: error.to_string(),
                            },
                        ),
                        None,
                    ));
                }
            };
        if template.quality < self.state.enrollment.min_quality {
            return Ok((
                rejected_attempt(
                    user_id,
                    Some(template.quality),
                    EnrollmentRejectionReason::LowQuality {
                        quality: template.quality,
                        min_quality: self.state.enrollment.min_quality,
                    },
                ),
                None,
            ));
        }
        if let Some(duplicate) = duplicate_match(
            &template,
            &self.state.templates,
            self.state.enrollment.duplicate,
            self.state.extractor,
            self.limits,
        )? {
            return Ok((
                rejected_attempt(
                    user_id,
                    Some(template.quality),
                    EnrollmentRejectionReason::DuplicateOfOtherUser { duplicate },
                ),
                None,
            ));
        }
        Ok((
            EnrollmentAttempt {
                user_id: user_id.to_owned(),
                record_id: Some(record_id),
                quality: Some(template.quality),
                accepted: true,
                rejection: None,
            },
            Some(template),
        ))
    }

    fn persist(&mut self) -> SdkResult<()> {
        let next_revision = self
            .state
            .revision
            .checked_add(1)
            .ok_or_else(|| SdkError::conflict("enrollment revision is exhausted"))?;
        let mut persisted = self.state.clone();
        persisted.revision = next_revision;
        validate_session_state(&persisted, self.limits)?;
        let bytes = encode_session_state(&persisted, self.limits)?;
        atomic_write_bytes(&session_path(&self.storage_root), &bytes)?;
        self.state.revision = next_revision;
        Ok(())
    }
}

impl LocationIndexBundle {
    /// Replace one user's enrollment using default acceptance policy.
    pub fn enroll_user<I, R>(
        &mut self,
        user_id: impl Into<String>,
        captures: I,
    ) -> SdkResult<EnrollmentDeltaResult>
    where
        I: IntoIterator<Item = R>,
        R: AsRef<[u8]>,
    {
        self.enroll_user_with_config(user_id, captures, EnrollmentConfig::default())
    }

    /// Replace one user's enrollment using explicit acceptance policy.
    pub fn enroll_user_with_config<I, R>(
        &mut self,
        user_id: impl Into<String>,
        captures: I,
        config: EnrollmentConfig,
    ) -> SdkResult<EnrollmentDeltaResult>
    where
        I: IntoIterator<Item = R>,
        R: AsRef<[u8]>,
    {
        let user_id = user_id.into();
        validate_identifier("user_id", &user_id)?;
        let limits = SdkLimits::default();
        let config = config.validate(limits)?;
        let extractor = self.manifest().extractor_config();
        let existing_templates = self.templates_for_enrollment();
        let mut attempts = Vec::new();
        let mut accepted_templates = Vec::new();
        let mut abort_duplicate = false;

        for raw in captures {
            if accepted_templates.len() >= config.max_templates_per_user {
                attempts.push(rejected_attempt(
                    &user_id,
                    None,
                    EnrollmentRejectionReason::MaxTemplatesForUser {
                        max_templates: config.max_templates_per_user,
                    },
                ));
                continue;
            }
            let record_id = format!("r{}", new_operation_id());
            let template = match extract_raw_bytes(
                record_id.clone(),
                user_id.clone(),
                raw.as_ref(),
                extractor,
            ) {
                Ok(template) => template,
                Err(error) => {
                    attempts.push(rejected_attempt(
                        &user_id,
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
                    &user_id,
                    Some(template.quality),
                    EnrollmentRejectionReason::LowQuality {
                        quality: template.quality,
                        min_quality: config.min_quality,
                    },
                ));
                continue;
            }
            if let Some(duplicate) = duplicate_match(
                &template,
                &existing_templates,
                config.duplicate,
                extractor,
                limits,
            )? {
                attempts.push(rejected_attempt(
                    &user_id,
                    Some(template.quality),
                    EnrollmentRejectionReason::DuplicateOfOtherUser { duplicate },
                ));
                abort_duplicate = true;
                break;
            }
            attempts.push(EnrollmentAttempt {
                user_id: user_id.clone(),
                record_id: Some(record_id),
                quality: Some(template.quality),
                accepted: true,
                rejection: None,
            });
            accepted_templates.push(template);
        }

        if abort_duplicate {
            for attempt in &mut attempts {
                if attempt.accepted {
                    attempt.accepted = false;
                    attempt.record_id = None;
                    attempt.rejection = Some(EnrollmentRejectionReason::NotCommitted {
                        message: "batch contained a cross-user duplicate".to_owned(),
                    });
                }
            }
            return Ok(EnrollmentDeltaResult {
                report: operation_report(self.manifest().location_id().to_owned(), attempts),
                delta: None,
            });
        }
        if accepted_templates.is_empty() {
            return Ok(EnrollmentDeltaResult {
                report: operation_report(self.manifest().location_id().to_owned(), attempts),
                delta: None,
            });
        }
        let delta = self.replace_user_templates(user_id, accepted_templates)?;
        Ok(EnrollmentDeltaResult {
            report: operation_report(self.manifest().location_id().to_owned(), attempts),
            delta: Some(delta),
        })
    }
}

fn duplicate_match(
    template: &ExtractedTemplate,
    existing_templates: &[ExtractedTemplate],
    config: DuplicateCheckConfig,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<Option<DuplicateEnrollmentMatch>> {
    if !config.enabled {
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
        .search_users(template, config.search)?
        .into_iter()
        .find(|hit| {
            hit.score >= config.min_score && hit.verification_score >= config.min_verification_score
        })
        .map(|hit| DuplicateEnrollmentMatch {
            user_id: hit.user_id,
            record_id: hit.record_id,
            score: hit.score,
            verification_score: hit.verification_score,
        }))
}

fn final_duplicate_audit(
    templates: &[ExtractedTemplate],
    config: DuplicateCheckConfig,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<Option<DuplicateEnrollmentMatch>> {
    if !config.enabled || templates.len() < 2 {
        return Ok(None);
    }
    let index = BiometricIndex::build_with_config(templates, extractor, limits)?;
    for template in templates {
        if let Some(duplicate) = index
            .search_records(template, config.search)?
            .into_iter()
            .find(|hit| {
                hit.user_id != template.record.user_id
                    && hit.score >= config.min_score
                    && hit.verification_score >= config.min_verification_score
            })
            .map(|hit| DuplicateEnrollmentMatch {
                user_id: hit.user_id,
                record_id: hit.record_id,
                score: hit.score,
                verification_score: hit.verification_score,
            })
        {
            return Ok(Some(duplicate));
        }
    }
    Ok(None)
}

fn rejected_attempt(
    user_id: &str,
    quality: Option<u8>,
    rejection: EnrollmentRejectionReason,
) -> EnrollmentAttempt {
    EnrollmentAttempt {
        user_id: user_id.to_owned(),
        record_id: None,
        quality,
        accepted: false,
        rejection: Some(rejection),
    }
}

fn report_from_state(state: &EnrollmentSessionState) -> EnrollmentReport {
    EnrollmentReport {
        location_id: state.location_id.clone(),
        accepted_records: state.templates.len(),
        accepted_users: user_count(&state.templates),
        rejected_captures: state
            .attempts
            .iter()
            .filter(|attempt| !attempt.accepted)
            .count(),
        attempts: state.attempts.clone(),
    }
}

fn operation_report(location_id: String, attempts: Vec<EnrollmentAttempt>) -> EnrollmentReport {
    EnrollmentReport {
        location_id,
        accepted_records: attempts.iter().filter(|attempt| attempt.accepted).count(),
        accepted_users: attempts
            .iter()
            .filter(|attempt| attempt.accepted)
            .map(|attempt| attempt.user_id.as_str())
            .collect::<BTreeSet<_>>()
            .len(),
        rejected_captures: attempts.iter().filter(|attempt| !attempt.accepted).count(),
        attempts,
    }
}

fn user_count(templates: &[ExtractedTemplate]) -> usize {
    templates
        .iter()
        .map(|template| template.record.user_id.as_str())
        .collect::<BTreeSet<_>>()
        .len()
}

fn validate_session_state(state: &EnrollmentSessionState, limits: SdkLimits) -> SdkResult<()> {
    validate_identifier("location_id", &state.location_id)?;
    state.enrollment.validate(limits)?;
    state.extractor.validate(limits)?;
    if state.identify != state.identify.normalized() {
        return Err(SdkError::integrity("session identify policy is invalid"));
    }
    if state.templates.len() > limits.max_records {
        return Err(SdkError::resource_limit("session template limit exceeded"));
    }
    if state.attempts.len() > limits.max_records.saturating_mul(16) {
        return Err(SdkError::resource_limit("session attempt limit exceeded"));
    }
    let mut record_ids = HashSet::new();
    let mut record_users = HashMap::new();
    for template in &state.templates {
        template.validate(state.extractor, limits)?;
        if !record_ids.insert(template.record.record_id.as_str()) {
            return Err(SdkError::integrity("session contains duplicate record ids"));
        }
        record_users.insert(
            template.record.record_id.as_str(),
            template.record.user_id.as_str(),
        );
    }
    let mut accepted_record_ids = HashSet::new();
    for attempt in &state.attempts {
        validate_identifier("attempt user_id", &attempt.user_id)?;
        if attempt.accepted {
            let record_id = attempt
                .record_id
                .as_deref()
                .ok_or_else(|| SdkError::integrity("accepted session attempt has no record id"))?;
            validate_identifier("attempt record_id", record_id)?;
            if attempt.quality.is_none()
                || attempt.rejection.is_some()
                || record_users.get(record_id).copied() != Some(attempt.user_id.as_str())
                || !accepted_record_ids.insert(record_id)
            {
                return Err(SdkError::integrity(
                    "accepted session attempt is inconsistent with templates",
                ));
            }
        } else {
            if attempt.record_id.is_some() || attempt.rejection.is_none() {
                return Err(SdkError::integrity(
                    "rejected session attempt has invalid outcome fields",
                ));
            }
            if let Some(rejection) = &attempt.rejection {
                validate_rejection(&attempt.user_id, rejection, limits)?;
            }
        }
    }
    if accepted_record_ids != record_ids {
        return Err(SdkError::integrity(
            "session templates and accepted attempts differ",
        ));
    }
    Ok(())
}

fn validate_rejection(
    attempt_user_id: &str,
    rejection: &EnrollmentRejectionReason,
    limits: SdkLimits,
) -> SdkResult<()> {
    match rejection {
        EnrollmentRejectionReason::InvalidCapture { message }
        | EnrollmentRejectionReason::NotCommitted { message } => {
            if message.is_empty() || message.len() > limits.max_string_bytes {
                return Err(SdkError::integrity("session rejection message is invalid"));
            }
        }
        EnrollmentRejectionReason::LowQuality {
            quality,
            min_quality,
        } => {
            if *min_quality > 100 || quality >= min_quality {
                return Err(SdkError::integrity(
                    "session low-quality rejection is inconsistent",
                ));
            }
        }
        EnrollmentRejectionReason::MaxTemplatesForUser { max_templates } => {
            if !(1..=16).contains(max_templates) {
                return Err(SdkError::integrity(
                    "session template-limit rejection is invalid",
                ));
            }
        }
        EnrollmentRejectionReason::DuplicateOfOtherUser { duplicate } => {
            validate_identifier("duplicate user_id", &duplicate.user_id)?;
            validate_identifier("duplicate record_id", &duplicate.record_id)?;
            if duplicate.user_id == attempt_user_id
                || !duplicate.score.is_finite()
                || !(0.0..=1.0).contains(&duplicate.score)
                || !duplicate.verification_score.is_finite()
                || !(0.0..=1.0).contains(&duplicate.verification_score)
            {
                return Err(SdkError::integrity(
                    "session duplicate rejection is invalid",
                ));
            }
        }
    }
    Ok(())
}

fn encode_session_state(state: &EnrollmentSessionState, limits: SdkLimits) -> SdkResult<Vec<u8>> {
    validate_session_state(state, limits)?;
    let payload = serde_json::to_vec(state)
        .map_err(|error| SdkError::serialization("encode enrollment session", error))?;
    if payload.len() > limits.max_bundle_bytes {
        return Err(SdkError::resource_limit(
            "enrollment session exceeds byte limit",
        ));
    }
    let mut bytes = Vec::new();
    bytes.write_all(SESSION_MAGIC)?;
    bytes.write_all(&(payload.len() as u64).to_le_bytes())?;
    bytes.write_all(&Sha256::digest(&payload))?;
    bytes.write_all(&payload)?;
    Ok(bytes)
}

fn decode_session_state(bytes: &[u8], limits: SdkLimits) -> SdkResult<EnrollmentSessionState> {
    if bytes.len() > limits.max_bundle_bytes {
        return Err(SdkError::resource_limit(
            "enrollment session exceeds byte limit",
        ));
    }
    let mut cursor = Cursor::new(bytes);
    let mut magic = [0; 8];
    cursor.read_exact(&mut magic)?;
    if &magic != SESSION_MAGIC {
        return Err(SdkError::invalid_format("invalid enrollment session magic"));
    }
    let mut len = [0; 8];
    cursor.read_exact(&mut len)?;
    let payload_len = usize::try_from(u64::from_le_bytes(len))
        .map_err(|_| SdkError::resource_limit("session payload exceeds usize"))?;
    if payload_len > limits.max_bundle_bytes {
        return Err(SdkError::resource_limit(
            "session payload exceeds byte limit",
        ));
    }
    let mut expected_hash = [0; SESSION_HASH_LEN];
    cursor.read_exact(&mut expected_hash)?;
    let mut payload = Vec::new();
    payload
        .try_reserve_exact(payload_len)
        .map_err(|_| SdkError::resource_limit("cannot reserve session payload"))?;
    payload.resize(payload_len, 0);
    cursor.read_exact(&mut payload)?;
    if Sha256::digest(&payload).as_slice() != expected_hash {
        return Err(SdkError::integrity("enrollment session hash mismatch"));
    }
    let mut extra = [0];
    if cursor.read(&mut extra)? != 0 {
        return Err(SdkError::invalid_format(
            "enrollment session has trailing bytes",
        ));
    }
    serde_json::from_slice(&payload)
        .map_err(|error| SdkError::serialization("decode enrollment session", error))
}

fn acquire_lock(path: &Path, busy_message: &str, enrollment: bool) -> SdkResult<File> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| {
            SdkError::io(format!("create lock directory {}", parent.display()), error)
        })?;
    }
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(path)
        .map_err(|error| SdkError::io(format!("open lock {}", path.display()), error))?;
    FileExt::try_lock_exclusive(&file).map_err(|error| {
        if error.kind() == std::io::ErrorKind::WouldBlock {
            if enrollment {
                SdkError::session_active(busy_message)
            } else {
                SdkError::conflict(busy_message)
            }
        } else {
            SdkError::io(format!("lock {}", path.display()), error)
        }
    })?;
    Ok(file)
}

fn sync_directory(path: &Path) -> SdkResult<()> {
    File::open(path)
        .and_then(|directory| directory.sync_all())
        .map_err(|error| SdkError::io(format!("sync directory {}", path.display()), error))
}

fn session_path(storage_root: &Path) -> PathBuf {
    storage_root.join("enrollment/current.bioenroll")
}

fn bundle_path(storage_root: &Path, location_id: &str) -> PathBuf {
    storage_root
        .join("bundles")
        .join(format!("{}.biobundle", location_storage_key(location_id)))
}

fn index_cache_path(storage_root: &Path, location_id: &str) -> PathBuf {
    storage_root
        .join("indexes")
        .join(format!("{}.bioindex", location_storage_key(location_id)))
}

fn read_index_cache_best_effort(path: &Path, max_index_bytes: usize) -> Option<Vec<u8>> {
    let maximum = max_index_bytes.checked_add(128)?;
    let metadata = fs::metadata(path).ok()?;
    if metadata.len() > maximum as u64 {
        return None;
    }
    fs::read(path).ok()
}

fn write_index_cache_best_effort(storage_root: &Path, bundle: &LocationIndexBundle) {
    let Ok(bytes) = bundle.index_cache_bytes() else {
        return;
    };
    let path = index_cache_path(storage_root, bundle.manifest().location_id());
    let _ = atomic_write_bytes(&path, &bytes);
}

#[cfg(test)]
mod tests {
    use super::super::error::SdkErrorCode;
    use super::*;
    use crate::fingerprint::{RAW_HEIGHT, RAW_LEN, RAW_WIDTH};

    #[test]
    fn default_quality_is_calibrated_at_65() {
        assert_eq!(EnrollmentConfig::default().min_quality, 65);
    }

    #[test]
    fn session_lease_prevents_multiple_mutable_handles() {
        let sdk = test_sdk();
        let session = sdk.start_enrollment_session("school").unwrap();
        let error = sdk.resume_enrollment_session().unwrap_err();
        assert_eq!(error.code(), SdkErrorCode::SessionActive);
        drop(session);
        assert!(sdk.resume_enrollment_session().is_ok());
    }

    #[test]
    fn initial_session_persists_resumes_and_closes() {
        let sdk = test_sdk();
        let mut session = sdk.start_enrollment_session("school").unwrap();
        let attempt = session.add_capture("A", &ridge_pattern(1)).unwrap();
        assert!(attempt.accepted, "{:?}", attempt.rejection);
        drop(session);
        let resumed = sdk.resume_enrollment_session().unwrap();
        assert_eq!(resumed.summary().accepted_records, 1);
        let result = resumed.close().unwrap();
        assert_eq!(result.bundle.stats().records, 1);
        assert!(!sdk.has_active_enrollment_session().unwrap());
    }

    #[test]
    fn removing_draft_user_updates_attempt_outcomes() {
        let sdk = test_sdk();
        let mut session = sdk.start_enrollment_session("school").unwrap();
        assert!(
            session
                .add_capture("A", &ridge_pattern(6))
                .unwrap()
                .accepted
        );

        session.remove_user("A").unwrap();
        let report = session.report();
        assert_eq!(report.accepted_records, 0);
        assert_eq!(report.rejected_captures, 1);
        assert!(!report.attempts[0].accepted);
        assert!(matches!(
            report.attempts[0].rejection,
            Some(EnrollmentRejectionReason::NotCommitted { .. })
        ));
    }

    #[test]
    fn final_duplicate_audit_detects_cross_user_copy_with_one_index() {
        let raw = ridge_pattern(7);
        let extractor = ExtractorConfig::default();
        let first = extract_raw_bytes("r1", "A", &raw, extractor).unwrap();
        let second = extract_raw_bytes("r2", "B", &raw, extractor).unwrap();

        let duplicate = final_duplicate_audit(
            &[first, second],
            EnrollmentConfig::default().duplicate,
            extractor,
            SdkLimits::default(),
        )
        .unwrap();
        assert!(duplicate.is_some());
    }

    #[test]
    fn location_storage_paths_do_not_collide() {
        let sdk = test_sdk();
        assert_ne!(sdk.bundle_path("school/a"), sdk.bundle_path("school?a"));
    }

    #[test]
    fn filesystem_index_cache_is_recreated_after_a_miss_or_corruption() {
        let sdk = test_sdk();
        let bundle = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        sdk.save_bundle(&bundle).unwrap();
        let cache_path = sdk.index_cache_path("school");
        assert!(cache_path.exists());

        fs::write(&cache_path, b"damaged cache").unwrap();
        assert_eq!(sdk.load_bundle("school").unwrap(), bundle);
        assert_ne!(fs::read(&cache_path).unwrap(), b"damaged cache");

        fs::remove_file(&cache_path).unwrap();
        assert_eq!(sdk.load_bundle("school").unwrap(), bundle);
        assert!(cache_path.exists());
    }

    #[test]
    fn initial_enrollment_cannot_replace_existing_location() {
        let sdk = test_sdk();
        sdk.save_bundle(&LocationIndexBundle::build("school", TemplateStore::new()).unwrap())
            .unwrap();
        assert_eq!(
            sdk.start_enrollment_session("school").unwrap_err().code(),
            SdkErrorCode::Conflict
        );
    }

    #[test]
    fn failed_delta_write_does_not_advance_persisted_bundle() {
        let sdk = test_sdk();
        let bundle = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        sdk.save_bundle(&bundle).unwrap();
        fs::write(sdk.delta_directory("school"), b"not a directory").unwrap();
        assert!(sdk.enroll_user("school", "A", [ridge_pattern(2)]).is_err());
        let persisted = LocationIndexBundle::load_bundle(sdk.bundle_path("school")).unwrap();
        assert_eq!(persisted.manifest().generation(), 0);
        assert_eq!(persisted.stats().records, 0);
    }

    #[test]
    fn durable_delta_is_replayed_after_interrupted_bundle_save() {
        let sdk = test_sdk();
        sdk.save_bundle(&LocationIndexBundle::build("school", TemplateStore::new()).unwrap())
            .unwrap();
        let mut source = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        let result = source
            .enroll_user_with_config("A", [ridge_pattern(3)], sdk.enrollment_config())
            .unwrap();
        let delta = result.delta.unwrap();

        sdk.save_delta(&delta).unwrap();
        let recovered = sdk.load_bundle("school").unwrap();
        assert_eq!(recovered.manifest().generation(), 1);
        assert_eq!(recovered.stats().records, 1);

        let replayed = sdk.load_bundle("school").unwrap();
        assert_eq!(replayed.manifest().generation(), 1);
        assert_eq!(replayed.stats().records, 1);
    }

    #[test]
    fn import_rejects_rollback_and_same_generation_divergence() {
        let sdk = test_sdk();
        let initial = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        sdk.save_bundle(&initial).unwrap();

        let mut advanced = initial.clone();
        advanced
            .enroll_user_with_config("A", [ridge_pattern(4)], sdk.enrollment_config())
            .unwrap();
        sdk.import_bundle("school", Cursor::new(advanced.to_bundle_bytes().unwrap()))
            .unwrap();

        let rollback = sdk
            .import_bundle("school", Cursor::new(initial.to_bundle_bytes().unwrap()))
            .unwrap_err();
        assert_eq!(rollback.code(), SdkErrorCode::Conflict);

        let mut divergent = initial;
        divergent
            .enroll_user_with_config("B", [ridge_pattern(8)], sdk.enrollment_config())
            .unwrap();
        let conflict = sdk
            .import_bundle("school", Cursor::new(divergent.to_bundle_bytes().unwrap()))
            .unwrap_err();
        assert_eq!(conflict.code(), SdkErrorCode::Conflict);
        assert_eq!(sdk.load_bundle("school").unwrap(), advanced);
    }

    #[test]
    fn removing_user_persists_and_replays_idempotently() {
        let sdk = test_sdk();
        let mut bundle = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        bundle
            .enroll_user_with_config("A", [ridge_pattern(5)], sdk.enrollment_config())
            .unwrap();
        sdk.save_bundle(&bundle).unwrap();

        let delta = sdk.remove_user("school", "A").unwrap();
        let removed = sdk.load_bundle("school").unwrap();
        assert_eq!(removed.manifest().generation(), 2);
        assert_eq!(removed.stats().records, 0);
        assert_eq!(
            sdk.apply_delta("school", &delta).unwrap(),
            DeltaApplyStatus::AlreadyApplied
        );
        assert_eq!(sdk.load_bundle("school").unwrap().stats().records, 0);
    }

    fn test_sdk() -> BiometricSdk {
        let root = std::env::temp_dir().join(format!("biometric-sdk-test-{}", new_operation_id()));
        BiometricSdk::open(SdkConfig::new(root).with_enrollment_min_quality(20)).unwrap()
    }

    fn ridge_pattern(seed: u8) -> Vec<u8> {
        let mut raw = vec![255; RAW_LEN];
        let center_x = RAW_WIDTH as f32 / 2.0 + f32::from(seed);
        let center_y = RAW_HEIGHT as f32 / 2.0;
        for y in 0..RAW_HEIGHT as usize {
            for x in 0..RAW_WIDTH as usize {
                let nx = (x as f32 - center_x) / 155.0;
                let ny = (y as f32 - center_y) / 215.0;
                if nx * nx + ny * ny <= 1.0 {
                    let radius =
                        ((x as f32 - center_x).powi(2) + (y as f32 - center_y).powi(2)).sqrt();
                    let angle = (y as f32 - center_y).atan2(x as f32 - center_x);
                    raw[y * RAW_WIDTH as usize + x] = if (radius * 0.55 + angle * 2.0).sin() > 0.0 {
                        35
                    } else {
                        220
                    };
                }
            }
        }
        raw
    }
}
