//! Location bundle, template store, and conflict-detecting sync deltas.

use std::collections::{BTreeMap, HashSet};
use std::fs;
use std::io::{Cursor, Read, Write};
use std::path::Path;

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::error::{SdkError, SdkResult};
use super::extractor::{
    ExtractedTemplate, ExtractorConfig, FingerRecord, TemplateFeature, extract_raw_bytes,
};
use super::index::{BiometricIndex, IdentifyConfig, IdentifyResult, SearchConfig, SearchHit};
use super::limits::SdkLimits;
use super::storage::{
    atomic_write, checked_next_sequence, hex, new_operation_id, validate_identifier,
};

const TEMPLATE_MAGIC: &[u8; 8] = b"BMSTPL\0\0";
const BUNDLE_MAGIC: &[u8; 8] = b"BMSBND\0\0";
const INDEX_CACHE_MAGIC: &[u8; 8] = b"BMSICHE\0";
const SECTION_HASH_LEN: usize = 32;
const BUNDLE_HEADER_LEN: usize = 8 + 8 * 2 + SECTION_HASH_LEN * 2;
const INDEX_CACHE_HEADER_LEN: usize = 8 + SECTION_HASH_LEN + 8 + SECTION_HASH_LEN;

/*
Bundle framing is deliberately fixed and small:

    magic
    manifest/templates lengths
    manifest/templates SHA-256 hashes
    two section payloads

Lengths are checked as a group before any section-sized allocation. The hashes
protect accidental corruption. The searchable index is derived from these
canonical sections and is intentionally absent from synchronized snapshots.
Filesystem-backed SDK instances may keep a disposable local index cache keyed
to the exact template bytes and extractor profile.
*/

/// Monotonic canonical sequence for one location.
pub type SyncSequence = u64;

/// Receipt retained for an applied delta.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AppliedDeltaReceipt {
    sequence: SyncSequence,
    delta_id: String,
    payload_hash: String,
}

impl AppliedDeltaReceipt {
    /// Canonical sequence applied to the bundle.
    pub fn sequence(&self) -> SyncSequence {
        self.sequence
    }

    /// Globally unique operation identifier.
    pub fn delta_id(&self) -> &str {
        &self.delta_id
    }

    /// SHA-256 hash of the exact encoded delta.
    pub fn payload_hash(&self) -> &str {
        &self.payload_hash
    }
}

/// Metadata and matching profile for one location snapshot.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LocationManifest {
    location_id: String,
    generation: SyncSequence,
    extractor: ExtractorConfig,
    identify: IdentifyConfig,
    record_count: u32,
    user_count: u32,
    applied_deltas: Vec<AppliedDeltaReceipt>,
}

impl LocationManifest {
    /// Location, school, or class identifier.
    pub fn location_id(&self) -> &str {
        &self.location_id
    }

    /// Latest canonical delta sequence in this snapshot.
    pub fn generation(&self) -> SyncSequence {
        self.generation
    }

    /// Extractor profile required for enrollment and queries.
    pub fn extractor_config(&self) -> ExtractorConfig {
        self.extractor
    }

    /// Default identify policy owned by this bundle.
    pub fn identify_config(&self) -> IdentifyConfig {
        self.identify
    }

    /// Finger-record count.
    pub fn record_count(&self) -> usize {
        self.record_count as usize
    }

    /// Distinct user count.
    pub fn user_count(&self) -> usize {
        self.user_count as usize
    }

    /// Applied delta receipts retained for replay/conflict detection.
    pub fn applied_deltas(&self) -> &[AppliedDeltaReceipt] {
        &self.applied_deltas
    }
}

/// Ordered extracted templates keyed by SDK finger record id.
#[derive(Debug, Clone, PartialEq)]
pub struct TemplateStore {
    templates: BTreeMap<String, ExtractedTemplate>,
}

impl TemplateStore {
    /// Create an empty template store.
    pub fn new() -> Self {
        Self {
            templates: BTreeMap::new(),
        }
    }

    /// Construct a store while rejecting duplicate record ids.
    pub fn from_templates(templates: Vec<ExtractedTemplate>) -> SdkResult<Self> {
        let mut store = Self::new();
        for template in templates {
            if store.templates.contains_key(&template.record.record_id) {
                return Err(SdkError::conflict(format!(
                    "duplicate template record id: {}",
                    template.record.record_id
                )));
            }
            store.upsert(template)?;
        }
        Ok(store)
    }

    /// Insert or replace a record without allowing ownership reassignment.
    pub fn upsert(&mut self, template: ExtractedTemplate) -> SdkResult<()> {
        if let Some(existing) = self.templates.get(&template.record.record_id)
            && existing.record.user_id != template.record.user_id
        {
            return Err(SdkError::conflict(format!(
                "record {} already belongs to another user",
                template.record.record_id
            )));
        }
        self.templates
            .insert(template.record.record_id.clone(), template);
        Ok(())
    }

    /// Remove one finger record.
    pub fn remove_record(&mut self, record_id: &str) -> bool {
        self.templates.remove(record_id).is_some()
    }

    /// Remove every finger record owned by one user.
    pub fn remove_user(&mut self, user_id: &str) -> usize {
        let before = self.templates.len();
        self.templates
            .retain(|_, template| template.record.user_id != user_id);
        before - self.templates.len()
    }

    /// Return templates in stable record-id order.
    pub fn templates(&self) -> Vec<ExtractedTemplate> {
        self.templates.values().cloned().collect()
    }

    /// Number of finger records.
    pub fn len(&self) -> usize {
        self.templates.len()
    }

    /// Whether the store contains no records.
    pub fn is_empty(&self) -> bool {
        self.templates.is_empty()
    }

    /// Number of distinct application users.
    pub fn user_count(&self) -> usize {
        self.templates
            .values()
            .map(|template| template.record.user_id.as_str())
            .collect::<HashSet<_>>()
            .len()
    }

    /// Encode using the default extraction profile and limits.
    pub fn to_bytes(&self) -> SdkResult<Vec<u8>> {
        encode_templates(
            &self.templates(),
            ExtractorConfig::default(),
            SdkLimits::default(),
        )
    }

    /// Decode using the default extraction profile and limits.
    pub fn from_bytes(bytes: &[u8]) -> SdkResult<Self> {
        decode_template_store(bytes, ExtractorConfig::default(), SdkLimits::default())
    }
}

impl Default for TemplateStore {
    fn default() -> Self {
        Self::new()
    }
}

/// Human-readable bundle sizing summary.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct BundleStats {
    /// Location identifier.
    pub location_id: String,
    /// Canonical generation.
    pub generation: SyncSequence,
    /// Finger records.
    pub records: usize,
    /// Distinct users.
    pub users: usize,
}

/// Searchable snapshot for one location.
#[derive(Debug, Clone)]
pub struct LocationIndexBundle {
    manifest: LocationManifest,
    store: TemplateStore,
    index: BiometricIndex,
    limits: SdkLimits,
}

impl PartialEq for LocationIndexBundle {
    fn eq(&self, other: &Self) -> bool {
        self.manifest == other.manifest && self.store == other.store && self.index == other.index
    }
}

/// Separate snapshot sections for BLOB-oriented storage engines.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LocationBundleBytes {
    /// JSON manifest bytes.
    pub manifest_json: Vec<u8>,
    /// Binary template-store bytes.
    pub templates: Vec<u8>,
}

/// Mutation carried by a sync delta.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case", deny_unknown_fields)]
pub enum DeltaOperation {
    /// Add or replace one finger owned by the same user.
    UpsertFinger {
        /// Extracted template to add or replace.
        template: ExtractedTemplate,
    },
    /// Remove one finger record.
    RemoveFinger {
        /// SDK-generated record identifier to remove.
        record_id: String,
    },
    /// Remove all records for one user.
    RemoveUser {
        /// Application user identifier to remove.
        user_id: String,
    },
    /// Replace all records for one user atomically.
    ReplaceUser {
        /// Application user identifier being replaced.
        user_id: String,
        /// Complete replacement template set.
        templates: Vec<ExtractedTemplate>,
    },
}

/// Location-scoped, uniquely identified canonical sync delta.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct IndexDelta {
    location_id: String,
    delta_id: String,
    base_generation: SyncSequence,
    sequence: SyncSequence,
    operation: DeltaOperation,
}

/// Outcome of applying an ordered delta.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum DeltaApplyStatus {
    /// The operation mutated the bundle.
    Applied,
    /// The exact same operation was already applied.
    AlreadyApplied,
}

impl LocationIndexBundle {
    /// Build a bundle using current default extraction and identify profiles.
    pub fn build(location_id: impl Into<String>, store: TemplateStore) -> SdkResult<Self> {
        Self::build_with_profiles(
            location_id,
            store,
            ExtractorConfig::default(),
            IdentifyConfig::default(),
            SdkLimits::default(),
        )
    }

    /// Build a bundle with explicit profiles and resource limits.
    pub fn build_with_profiles(
        location_id: impl Into<String>,
        store: TemplateStore,
        extractor: ExtractorConfig,
        identify: IdentifyConfig,
        limits: SdkLimits,
    ) -> SdkResult<Self> {
        let limits = limits.validate()?;
        let location_id = location_id.into();
        validate_identifier("location_id", &location_id)?;
        let extractor = extractor.validate(limits)?;
        let identify = identify.normalized();
        let templates = store.templates();
        let index = BiometricIndex::build_with_config(&templates, extractor, limits)?;
        let manifest = LocationManifest {
            location_id,
            generation: 0,
            extractor,
            identify,
            record_count: checked_u32(store.len(), "record count")?,
            user_count: checked_u32(store.user_count(), "user count")?,
            applied_deltas: Vec::new(),
        };
        let bundle = Self {
            manifest,
            store,
            index,
            limits,
        };
        bundle.validate()?;
        Ok(bundle)
    }

    /// Save atomically as one `.biobundle` file.
    pub fn save_bundle(&self, path: impl AsRef<Path>) -> SdkResult<()> {
        let path = path.as_ref();
        atomic_write(path, |writer| self.write_bundle(writer))
    }

    /// Load a bundle with default resource limits.
    pub fn load_bundle(path: impl AsRef<Path>) -> SdkResult<Self> {
        let path = path.as_ref();
        let file = fs::File::open(path)
            .map_err(|error| SdkError::io(format!("open bundle {}", path.display()), error))?;
        Self::read_bundle(file)
    }

    /// Encode into one owned byte vector.
    pub fn to_bundle_bytes(&self) -> SdkResult<Vec<u8>> {
        let mut bytes = Vec::new();
        self.write_bundle(&mut bytes)?;
        Ok(bytes)
    }

    /// Decode from one owned or borrowed byte slice.
    pub fn from_bundle_bytes(bytes: &[u8]) -> SdkResult<Self> {
        if bytes.len() > SdkLimits::default().max_bundle_bytes {
            return Err(SdkError::resource_limit("bundle exceeds max_bundle_bytes"));
        }
        Self::read_bundle(Cursor::new(bytes))
    }

    /// Write the bounded, hashed bundle container.
    pub fn write_bundle(&self, mut writer: impl Write) -> SdkResult<()> {
        let parts = self.to_binary_parts()?;
        validate_section_sizes(&parts, self.limits)?;
        writer.write_all(BUNDLE_MAGIC)?;
        write_u64(&mut writer, parts.manifest_json.len() as u64)?;
        write_u64(&mut writer, parts.templates.len() as u64)?;
        writer.write_all(&section_hash(&parts.manifest_json))?;
        writer.write_all(&section_hash(&parts.templates))?;
        writer.write_all(&parts.manifest_json)?;
        writer.write_all(&parts.templates)?;
        Ok(())
    }

    /// Read using default resource limits.
    pub fn read_bundle(reader: impl Read) -> SdkResult<Self> {
        Self::read_bundle_with_limits(reader, SdkLimits::default())
    }

    /// Read while enforcing caller-provided resource limits before allocation.
    pub fn read_bundle_with_limits(reader: impl Read, limits: SdkLimits) -> SdkResult<Self> {
        let parts = read_bundle_sections(reader, limits)?;
        Self::from_binary_parts_with_limits(&parts.manifest_json, &parts.templates, limits)
    }

    /// Read a canonical snapshot and reuse a verified local derived index cache.
    ///
    /// Cache failures are deliberately treated as misses. Canonical bundle
    /// corruption still fails, while a stale or damaged cache simply triggers a
    /// deterministic index rebuild from the validated templates.
    pub(crate) fn read_bundle_with_index_cache(
        reader: impl Read,
        cache: Option<&[u8]>,
        limits: SdkLimits,
    ) -> SdkResult<(Self, bool)> {
        let limits = limits.validate()?;
        let parts = read_bundle_sections(reader, limits)?;
        let manifest: LocationManifest = serde_json::from_slice(&parts.manifest_json)
            .map_err(|error| SdkError::serialization("decode location manifest", error))?;
        let store = decode_template_store(&parts.templates, manifest.extractor, limits)?;
        let source_hash = index_source_hash(&manifest, &parts.templates)?;
        if let Some(index) = cache.and_then(|bytes| decode_index_cache(bytes, source_hash, limits))
        {
            let bundle = Self {
                manifest,
                store,
                index,
                limits,
            };
            bundle.validate_components()?;
            return Ok((bundle, true));
        }
        let index =
            BiometricIndex::build_with_config(&store.templates(), manifest.extractor, limits)?;
        let bundle = Self {
            manifest,
            store,
            index,
            limits,
        };
        bundle.validate_components()?;
        Ok((bundle, false))
    }

    /// Export validated bundle sections.
    pub fn to_binary_parts(&self) -> SdkResult<LocationBundleBytes> {
        self.validate()?;
        let parts = LocationBundleBytes {
            manifest_json: serde_json::to_vec_pretty(&self.manifest)
                .map_err(|error| SdkError::serialization("encode location manifest", error))?,
            templates: encode_templates(
                &self.store.templates(),
                self.manifest.extractor,
                self.limits,
            )?,
        };
        validate_section_sizes(&parts, self.limits)?;
        Ok(parts)
    }

    /// Load validated bundle sections using default limits.
    pub fn from_binary_parts(manifest_json: &[u8], templates: &[u8]) -> SdkResult<Self> {
        Self::from_binary_parts_with_limits(manifest_json, templates, SdkLimits::default())
    }

    fn from_binary_parts_with_limits(
        manifest_json: &[u8],
        templates: &[u8],
        limits: SdkLimits,
    ) -> SdkResult<Self> {
        let manifest: LocationManifest = serde_json::from_slice(manifest_json)
            .map_err(|error| SdkError::serialization("decode location manifest", error))?;
        let store = decode_template_store(templates, manifest.extractor, limits)?;
        let index =
            BiometricIndex::build_with_config(&store.templates(), manifest.extractor, limits)?;
        let bundle = Self {
            manifest,
            store,
            index,
            limits,
        };
        bundle.validate_components()?;
        Ok(bundle)
    }

    /// Encode the disposable local index cache for filesystem-backed use.
    pub(crate) fn index_cache_bytes(&self) -> SdkResult<Vec<u8>> {
        self.validate_components()?;
        let templates = encode_templates(
            &self.store.templates(),
            self.manifest.extractor,
            self.limits,
        )?;
        let source_hash = index_source_hash(&self.manifest, &templates)?;
        let index = self.index.to_bytes()?;
        let capacity = INDEX_CACHE_HEADER_LEN
            .checked_add(index.len())
            .ok_or_else(|| SdkError::resource_limit("index cache length overflows usize"))?;
        let mut bytes = Vec::new();
        bytes
            .try_reserve_exact(capacity)
            .map_err(|_| SdkError::resource_limit("cannot reserve index cache"))?;
        bytes.write_all(INDEX_CACHE_MAGIC)?;
        bytes.write_all(&source_hash)?;
        write_u64(&mut bytes, index.len() as u64)?;
        bytes.write_all(&section_hash(&index))?;
        bytes.write_all(&index)?;
        Ok(bytes)
    }

    /// Bundle manifest and matching profile.
    pub fn manifest(&self) -> &LocationManifest {
        &self.manifest
    }

    /// Bundle sizing summary.
    pub fn stats(&self) -> BundleStats {
        BundleStats {
            location_id: self.manifest.location_id.clone(),
            generation: self.manifest.generation,
            records: self.store.len(),
            users: self.store.user_count(),
        }
    }

    /// Search a raw query using this bundle's extractor profile.
    pub fn search_raw_bytes(&self, raw: &[u8], config: SearchConfig) -> SdkResult<Vec<SearchHit>> {
        let query = extract_raw_bytes("query", "query", raw, self.manifest.extractor)?;
        self.index.search_users(&query, config)
    }

    /// Identify using the policy persisted in this bundle.
    pub fn identify_raw_bytes(&self, raw: &[u8]) -> SdkResult<IdentifyResult> {
        self.identify_raw_bytes_with_config(raw, self.manifest.identify)
    }

    /// Identify using an explicit app policy while retaining the bundle extractor.
    pub fn identify_raw_bytes_with_config(
        &self,
        raw: &[u8],
        config: IdentifyConfig,
    ) -> SdkResult<IdentifyResult> {
        let query = extract_raw_bytes("query", "query", raw, self.manifest.extractor)?;
        self.index.identify_user(&query, config)
    }

    /// Low-level upsert that emits and locally applies one conflict-detecting delta.
    pub fn upsert_raw_bytes(
        &mut self,
        record_id: impl Into<String>,
        user_id: impl Into<String>,
        raw: &[u8],
    ) -> SdkResult<IndexDelta> {
        let template = extract_raw_bytes(record_id, user_id, raw, self.manifest.extractor)?;
        self.create_and_apply(DeltaOperation::UpsertFinger { template })
    }

    /// Remove one finger and emit the applied delta.
    pub fn remove_record(&mut self, record_id: impl Into<String>) -> SdkResult<IndexDelta> {
        let record_id = record_id.into();
        validate_identifier("record_id", &record_id)?;
        self.create_and_apply(DeltaOperation::RemoveFinger { record_id })
    }

    /// Remove all records for one user and emit the applied delta.
    pub fn remove_user(&mut self, user_id: impl Into<String>) -> SdkResult<IndexDelta> {
        let user_id = user_id.into();
        validate_identifier("user_id", &user_id)?;
        self.create_and_apply(DeltaOperation::RemoveUser { user_id })
    }

    /// Apply an ordered delta atomically in memory.
    pub fn apply_delta(&mut self, delta: &IndexDelta) -> SdkResult<DeltaApplyStatus> {
        delta.validate_envelope()?;
        if delta.location_id != self.manifest.location_id {
            return Err(SdkError::conflict(format!(
                "delta location mismatch: bundle={}, delta={}",
                self.manifest.location_id, delta.location_id
            )));
        }
        let payload_hash = delta.payload_hash()?;
        if let Some(receipt) = self
            .manifest
            .applied_deltas
            .iter()
            .find(|receipt| receipt.delta_id == delta.delta_id)
        {
            if receipt.sequence == delta.sequence && receipt.payload_hash == payload_hash {
                return Ok(DeltaApplyStatus::AlreadyApplied);
            }
            return Err(SdkError::conflict(format!(
                "delta id {} was already applied at sequence {}",
                delta.delta_id, receipt.sequence
            )));
        }
        if let Some(receipt) = self
            .manifest
            .applied_deltas
            .iter()
            .find(|receipt| receipt.sequence == delta.sequence)
        {
            if receipt.delta_id == delta.delta_id && receipt.payload_hash == payload_hash {
                return Ok(DeltaApplyStatus::AlreadyApplied);
            }
            return Err(SdkError::conflict(format!(
                "sequence {} already contains a different delta",
                delta.sequence
            )));
        }
        if delta.sequence <= self.manifest.generation {
            return Err(SdkError::conflict(format!(
                "cannot verify stale delta {} because no matching receipt exists",
                delta.sequence
            )));
        }
        if delta.base_generation != self.manifest.generation {
            return Err(SdkError::conflict(format!(
                "delta base generation mismatch: bundle={}, delta={}",
                self.manifest.generation, delta.base_generation
            )));
        }
        if delta.sequence != checked_next_sequence(self.manifest.generation)? {
            return Err(SdkError::conflict(format!(
                "delta sequence gap: current={}, delta={}",
                self.manifest.generation, delta.sequence
            )));
        }
        if self.manifest.applied_deltas.len() >= self.limits.max_delta_history {
            return Err(SdkError::resource_limit(
                "applied delta history limit exceeded",
            ));
        }

        let mut staged_store = self.store.clone();
        apply_operation(
            &mut staged_store,
            &delta.operation,
            self.manifest.extractor,
            self.limits,
        )?;
        let staged_index = BiometricIndex::build_with_config(
            &staged_store.templates(),
            self.manifest.extractor,
            self.limits,
        )?;
        let mut staged_manifest = self.manifest.clone();
        staged_manifest.generation = delta.sequence;
        staged_manifest.record_count = checked_u32(staged_store.len(), "record count")?;
        staged_manifest.user_count = checked_u32(staged_store.user_count(), "user count")?;
        staged_manifest.applied_deltas.push(AppliedDeltaReceipt {
            sequence: delta.sequence,
            delta_id: delta.delta_id.clone(),
            payload_hash,
        });
        let staged = Self {
            manifest: staged_manifest,
            store: staged_store,
            index: staged_index,
            limits: self.limits,
        };
        staged.validate()?;
        *self = staged;
        Ok(DeltaApplyStatus::Applied)
    }

    pub(crate) fn templates_for_enrollment(&self) -> Vec<ExtractedTemplate> {
        self.store.templates()
    }

    pub(crate) fn replace_user_templates(
        &mut self,
        user_id: impl Into<String>,
        templates: Vec<ExtractedTemplate>,
    ) -> SdkResult<IndexDelta> {
        let user_id = user_id.into();
        validate_identifier("user_id", &user_id)?;
        self.create_and_apply(DeltaOperation::ReplaceUser { user_id, templates })
    }

    fn create_and_apply(&mut self, operation: DeltaOperation) -> SdkResult<IndexDelta> {
        let delta = IndexDelta {
            location_id: self.manifest.location_id.clone(),
            delta_id: new_operation_id(),
            base_generation: self.manifest.generation,
            sequence: checked_next_sequence(self.manifest.generation)?,
            operation,
        };
        let status = self.apply_delta(&delta)?;
        if status != DeltaApplyStatus::Applied {
            return Err(SdkError::conflict("new local delta was already applied"));
        }
        Ok(delta)
    }

    /// Validate manifest, templates, receipts, and exact hot-index consistency.
    pub fn validate(&self) -> SdkResult<()> {
        let extractor = self.validate_components()?;
        let rebuilt =
            BiometricIndex::build_with_config(&self.store.templates(), extractor, self.limits)?;
        if self.index != rebuilt {
            return Err(SdkError::integrity(
                "derived hot index does not match template store",
            ));
        }
        Ok(())
    }

    /// Validate canonical state plus the structural safety of a derived index.
    ///
    /// Exact template/index comparison belongs in [`Self::validate`]. Cache
    /// loading calls this cheaper form only after both cache hashes and the
    /// canonical source key have been verified.
    fn validate_components(&self) -> SdkResult<ExtractorConfig> {
        self.limits.validate()?;
        validate_identifier("location_id", &self.manifest.location_id)?;
        let extractor = self.manifest.extractor.validate(self.limits)?;
        if self.manifest.identify != self.manifest.identify.normalized() {
            return Err(SdkError::integrity("bundle identify policy is not valid"));
        }
        if self.manifest.record_count as usize != self.store.len()
            || self.manifest.user_count as usize != self.store.user_count()
        {
            return Err(SdkError::integrity(
                "bundle manifest counts do not match templates",
            ));
        }
        if self.store.len() > self.limits.max_records {
            return Err(SdkError::resource_limit("bundle record limit exceeded"));
        }
        for template in self.store.templates.values() {
            template.validate(extractor, self.limits)?;
        }
        if self.manifest.generation > self.limits.max_delta_history as u64
            || self.manifest.applied_deltas.len() != self.manifest.generation as usize
        {
            return Err(SdkError::integrity(
                "bundle generation and delta receipt history differ",
            ));
        }
        let mut expected_sequence = 1u64;
        let mut delta_ids = HashSet::new();
        for receipt in &self.manifest.applied_deltas {
            if receipt.sequence != expected_sequence
                || !is_lower_hex(&receipt.delta_id, 32)
                || !is_lower_hex(&receipt.payload_hash, 64)
                || !delta_ids.insert(receipt.delta_id.as_str())
            {
                return Err(SdkError::integrity(
                    "bundle delta receipt history is invalid",
                ));
            }
            expected_sequence = checked_next_sequence(expected_sequence)?;
        }
        self.index.validate(self.limits)?;
        Ok(extractor)
    }
}

impl IndexDelta {
    /// Location targeted by this operation.
    pub fn location_id(&self) -> &str {
        &self.location_id
    }

    /// Globally unique operation id.
    pub fn delta_id(&self) -> &str {
        &self.delta_id
    }

    /// Snapshot generation on which this operation was created.
    pub fn base_generation(&self) -> SyncSequence {
        self.base_generation
    }

    /// Canonical sequence assigned to this operation.
    pub fn sequence(&self) -> SyncSequence {
        self.sequence
    }

    /// Operation payload.
    pub fn operation(&self) -> &DeltaOperation {
        &self.operation
    }

    /// Rebase a pending operation after a canonical sync conflict.
    pub fn rebase(&self, base_generation: SyncSequence) -> SdkResult<Self> {
        Ok(Self {
            location_id: self.location_id.clone(),
            delta_id: self.delta_id.clone(),
            base_generation,
            sequence: checked_next_sequence(base_generation)?,
            operation: self.operation.clone(),
        })
    }

    /// JSON encoding used by the synchronization layer.
    pub fn to_json_bytes(&self) -> SdkResult<Vec<u8>> {
        self.validate_envelope()?;
        serde_json::to_vec(self)
            .map_err(|error| SdkError::serialization("encode index delta", error))
    }

    /// Decode and validate a JSON delta envelope.
    pub fn from_json_bytes(bytes: &[u8]) -> SdkResult<Self> {
        if bytes.len() > SdkLimits::default().max_template_bytes {
            return Err(SdkError::resource_limit(
                "delta exceeds configured byte limit",
            ));
        }
        let delta: Self = serde_json::from_slice(bytes)
            .map_err(|error| SdkError::serialization("decode index delta", error))?;
        delta.validate_envelope()?;
        Ok(delta)
    }

    fn validate_envelope(&self) -> SdkResult<()> {
        validate_identifier("delta location_id", &self.location_id)?;
        if !is_lower_hex(&self.delta_id, 32) {
            return Err(SdkError::invalid_input(
                "delta_id must contain 32 lowercase hexadecimal characters",
            ));
        }
        if self.sequence != checked_next_sequence(self.base_generation)? {
            return Err(SdkError::conflict(
                "delta sequence must immediately follow base_generation",
            ));
        }
        Ok(())
    }

    fn payload_hash(&self) -> SdkResult<String> {
        let bytes = serde_json::to_vec(self)
            .map_err(|error| SdkError::serialization("hash index delta", error))?;
        Ok(hex(&Sha256::digest(bytes)))
    }
}

fn apply_operation(
    store: &mut TemplateStore,
    operation: &DeltaOperation,
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<()> {
    match operation {
        DeltaOperation::UpsertFinger { template } => {
            template.validate(extractor, limits)?;
            store.upsert(template.clone())
        }
        DeltaOperation::RemoveFinger { record_id } => {
            validate_identifier("record_id", record_id)?;
            store.remove_record(record_id);
            Ok(())
        }
        DeltaOperation::RemoveUser { user_id } => {
            validate_identifier("user_id", user_id)?;
            store.remove_user(user_id);
            Ok(())
        }
        DeltaOperation::ReplaceUser { user_id, templates } => {
            replace_user_templates_in_store(store, user_id, templates, extractor, limits)
        }
    }
}

fn replace_user_templates_in_store(
    store: &mut TemplateStore,
    user_id: &str,
    templates: &[ExtractedTemplate],
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<()> {
    validate_identifier("user_id", user_id)?;
    if templates.len() > limits.max_records {
        return Err(SdkError::resource_limit(
            "replacement template count exceeds limit",
        ));
    }
    let mut record_ids = HashSet::new();
    for template in templates {
        template.validate(extractor, limits)?;
        if template.record.user_id != user_id {
            return Err(SdkError::conflict(format!(
                "replacement template {} belongs to another user",
                template.record.record_id
            )));
        }
        if !record_ids.insert(template.record.record_id.as_str()) {
            return Err(SdkError::conflict(format!(
                "replacement contains duplicate record {}",
                template.record.record_id
            )));
        }
        if let Some(existing) = store.templates.get(&template.record.record_id)
            && existing.record.user_id != user_id
        {
            return Err(SdkError::conflict(format!(
                "replacement record {} collides with another user",
                template.record.record_id
            )));
        }
    }
    store.remove_user(user_id);
    for template in templates {
        store.upsert(template.clone())?;
    }
    Ok(())
}

fn encode_templates(
    templates: &[ExtractedTemplate],
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<Vec<u8>> {
    if templates.len() > limits.max_records {
        return Err(SdkError::resource_limit(
            "template record count exceeds limit",
        ));
    }
    let mut bytes = Vec::new();
    bytes.write_all(TEMPLATE_MAGIC)?;
    write_u32(&mut bytes, checked_u32(templates.len(), "template count")?)?;
    for template in templates {
        template.validate(extractor, limits)?;
        write_string(&mut bytes, &template.record.record_id, limits)?;
        write_string(&mut bytes, &template.record.user_id, limits)?;
        bytes.write_all(&[template.quality])?;
        write_u16(&mut bytes, template.token_count)?;
        write_u16(
            &mut bytes,
            checked_u16(template.tokens.len(), "template token count")?,
        )?;
        for token in &template.tokens {
            write_u64(&mut bytes, *token)?;
        }
        write_u16(
            &mut bytes,
            checked_u16(template.features.len(), "template feature count")?,
        )?;
        for feature in &template.features {
            bytes.write_all(&[
                feature.x,
                feature.y,
                feature.orientation,
                feature.contrast,
                feature.coherence,
                feature.kind,
            ])?;
        }
        if bytes.len() > limits.max_template_bytes {
            return Err(SdkError::resource_limit(
                "encoded template section exceeds max_template_bytes",
            ));
        }
    }
    Ok(bytes)
}

fn decode_template_store(
    bytes: &[u8],
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<TemplateStore> {
    if bytes.len() > limits.max_template_bytes {
        return Err(SdkError::resource_limit(
            "template section exceeds max_template_bytes",
        ));
    }
    let mut cursor = Cursor::new(bytes);
    let mut magic = [0; 8];
    cursor.read_exact(&mut magic)?;
    if &magic != TEMPLATE_MAGIC {
        return Err(SdkError::invalid_format("invalid template store magic"));
    }
    let count = read_u32(&mut cursor)? as usize;
    if count > limits.max_records {
        return Err(SdkError::resource_limit(
            "template record count exceeds limit",
        ));
    }
    let mut templates = reserved_vec(count, "templates")?;
    for _ in 0..count {
        let record_id = read_string(&mut cursor, limits)?;
        let user_id = read_string(&mut cursor, limits)?;
        let mut quality = [0];
        cursor.read_exact(&mut quality)?;
        let token_count = read_u16(&mut cursor)?;
        let token_len = usize::from(read_u16(&mut cursor)?);
        if token_len > limits.max_tokens_per_template {
            return Err(SdkError::resource_limit(
                "template token count exceeds limit",
            ));
        }
        let mut tokens = reserved_vec(token_len, "template tokens")?;
        for _ in 0..token_len {
            tokens.push(read_u64(&mut cursor)?);
        }
        let feature_len = usize::from(read_u16(&mut cursor)?);
        if feature_len > limits.max_features_per_template {
            return Err(SdkError::resource_limit(
                "template feature count exceeds limit",
            ));
        }
        let mut features = reserved_vec(feature_len, "template features")?;
        for _ in 0..feature_len {
            let mut payload = [0; 6];
            cursor.read_exact(&mut payload)?;
            features.push(TemplateFeature {
                x: payload[0],
                y: payload[1],
                orientation: payload[2],
                contrast: payload[3],
                coherence: payload[4],
                kind: payload[5],
            });
        }
        let template = ExtractedTemplate {
            record: FingerRecord { record_id, user_id },
            quality: quality[0],
            token_count,
            tokens,
            features,
        };
        template.validate(extractor, limits)?;
        templates.push(template);
    }
    ensure_eof(&mut cursor, "template store")?;
    TemplateStore::from_templates(templates)
}

fn validate_section_sizes(parts: &LocationBundleBytes, limits: SdkLimits) -> SdkResult<()> {
    validate_decoded_section_sizes(parts.manifest_json.len(), parts.templates.len(), limits)
}

fn validate_decoded_section_sizes(
    manifest: usize,
    templates: usize,
    limits: SdkLimits,
) -> SdkResult<()> {
    if manifest > limits.max_manifest_bytes {
        return Err(SdkError::resource_limit("manifest section exceeds limit"));
    }
    if templates > limits.max_template_bytes {
        return Err(SdkError::resource_limit("template section exceeds limit"));
    }
    let total = BUNDLE_HEADER_LEN
        .checked_add(manifest)
        .and_then(|value| value.checked_add(templates))
        .ok_or_else(|| SdkError::resource_limit("bundle length overflows usize"))?;
    if total > limits.max_bundle_bytes {
        return Err(SdkError::resource_limit("bundle exceeds max_bundle_bytes"));
    }
    Ok(())
}

fn read_bundle_sections(
    mut reader: impl Read,
    limits: SdkLimits,
) -> SdkResult<LocationBundleBytes> {
    let limits = limits.validate()?;
    let mut magic = [0; 8];
    reader.read_exact(&mut magic)?;
    if &magic != BUNDLE_MAGIC {
        return Err(SdkError::invalid_format("invalid biometric bundle magic"));
    }
    let manifest_len = read_section_len(&mut reader, "manifest")?;
    let templates_len = read_section_len(&mut reader, "templates")?;
    validate_decoded_section_sizes(manifest_len, templates_len, limits)?;
    let expected_manifest_hash = read_section_hash(&mut reader, "manifest")?;
    let expected_templates_hash = read_section_hash(&mut reader, "templates")?;
    let manifest_json = read_section(&mut reader, manifest_len, "manifest")?;
    let templates = read_section(&mut reader, templates_len, "templates")?;
    verify_section_hash("manifest", &manifest_json, &expected_manifest_hash)?;
    verify_section_hash("templates", &templates, &expected_templates_hash)?;
    ensure_eof(&mut reader, "biometric bundle")?;
    Ok(LocationBundleBytes {
        manifest_json,
        templates,
    })
}

fn index_source_hash(
    manifest: &LocationManifest,
    templates: &[u8],
) -> SdkResult<[u8; SECTION_HASH_LEN]> {
    let extractor = serde_json::to_vec(&manifest.extractor)
        .map_err(|error| SdkError::serialization("encode index source profile", error))?;
    let mut hasher = Sha256::new();
    hasher.update(b"biometric-sdk-index-source");
    hasher.update((extractor.len() as u64).to_le_bytes());
    hasher.update(extractor);
    hasher.update(templates);
    Ok(hasher.finalize().into())
}

fn decode_index_cache(
    bytes: &[u8],
    expected_source_hash: [u8; SECTION_HASH_LEN],
    limits: SdkLimits,
) -> Option<BiometricIndex> {
    let maximum = INDEX_CACHE_HEADER_LEN.checked_add(limits.max_index_bytes)?;
    if bytes.len() < INDEX_CACHE_HEADER_LEN || bytes.len() > maximum {
        return None;
    }
    let mut cursor = Cursor::new(bytes);
    let mut magic = [0; 8];
    cursor.read_exact(&mut magic).ok()?;
    if &magic != INDEX_CACHE_MAGIC {
        return None;
    }
    let mut source_hash = [0; SECTION_HASH_LEN];
    cursor.read_exact(&mut source_hash).ok()?;
    if source_hash != expected_source_hash {
        return None;
    }
    let index_len = read_section_len(&mut cursor, "cached index").ok()?;
    if index_len > limits.max_index_bytes
        || INDEX_CACHE_HEADER_LEN.checked_add(index_len)? != bytes.len()
    {
        return None;
    }
    let expected_index_hash = read_section_hash(&mut cursor, "cached index").ok()?;
    let index = read_section(&mut cursor, index_len, "cached index").ok()?;
    verify_section_hash("cached index", &index, &expected_index_hash).ok()?;
    ensure_eof(&mut cursor, "index cache").ok()?;
    super::persist::decode_index(&index, limits).ok()
}

fn read_section_len(reader: impl Read, section: &str) -> SdkResult<usize> {
    usize::try_from(read_u64(reader)?)
        .map_err(|_| SdkError::resource_limit(format!("{section} section exceeds usize")))
}

fn read_section_hash(mut reader: impl Read, section: &str) -> SdkResult<[u8; SECTION_HASH_LEN]> {
    let mut hash = [0; SECTION_HASH_LEN];
    reader
        .read_exact(&mut hash)
        .map_err(|error| SdkError::io(format!("read {section} section hash"), error))?;
    Ok(hash)
}

fn read_section(mut reader: impl Read, len: usize, section: &str) -> SdkResult<Vec<u8>> {
    let mut bytes = reserved_vec(len, section)?;
    bytes.resize(len, 0);
    reader
        .read_exact(&mut bytes)
        .map_err(|error| SdkError::io(format!("read {section} section"), error))?;
    Ok(bytes)
}

fn section_hash(bytes: &[u8]) -> [u8; SECTION_HASH_LEN] {
    Sha256::digest(bytes).into()
}

fn verify_section_hash(
    section: &str,
    bytes: &[u8],
    expected: &[u8; SECTION_HASH_LEN],
) -> SdkResult<()> {
    if &section_hash(bytes) != expected {
        return Err(SdkError::integrity(format!(
            "{section} section hash mismatch"
        )));
    }
    Ok(())
}

fn reserved_vec<T>(capacity: usize, label: &str) -> SdkResult<Vec<T>> {
    let mut values = Vec::new();
    values
        .try_reserve_exact(capacity)
        .map_err(|_| SdkError::resource_limit(format!("cannot reserve {label}")))?;
    Ok(values)
}

fn write_string(mut writer: impl Write, value: &str, limits: SdkLimits) -> SdkResult<()> {
    if value.len() > limits.max_string_bytes {
        return Err(SdkError::resource_limit(
            "encoded string exceeds configured limit",
        ));
    }
    write_u16(&mut writer, checked_u16(value.len(), "encoded string")?)?;
    writer.write_all(value.as_bytes())?;
    Ok(())
}

fn read_string(mut reader: impl Read, limits: SdkLimits) -> SdkResult<String> {
    let len = usize::from(read_u16(&mut reader)?);
    if len > limits.max_string_bytes {
        return Err(SdkError::resource_limit(
            "encoded string exceeds configured limit",
        ));
    }
    let mut bytes = reserved_vec(len, "encoded string")?;
    bytes.resize(len, 0);
    reader.read_exact(&mut bytes)?;
    String::from_utf8(bytes).map_err(|_| SdkError::invalid_format("encoded string is not UTF-8"))
}

fn ensure_eof(mut reader: impl Read, label: &str) -> SdkResult<()> {
    let mut extra = [0; 1];
    if reader.read(&mut extra)? != 0 {
        return Err(SdkError::invalid_format(format!(
            "{label} has trailing bytes"
        )));
    }
    Ok(())
}

fn checked_u16(value: usize, label: &str) -> SdkResult<u16> {
    u16::try_from(value).map_err(|_| SdkError::resource_limit(format!("{label} exceeds u16")))
}

fn checked_u32(value: usize, label: &str) -> SdkResult<u32> {
    u32::try_from(value).map_err(|_| SdkError::resource_limit(format!("{label} exceeds u32")))
}

fn is_lower_hex(value: &str, len: usize) -> bool {
    value.len() == len
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn write_u16(mut writer: impl Write, value: u16) -> SdkResult<()> {
    writer.write_all(&value.to_le_bytes())?;
    Ok(())
}

fn write_u32(mut writer: impl Write, value: u32) -> SdkResult<()> {
    writer.write_all(&value.to_le_bytes())?;
    Ok(())
}

fn write_u64(mut writer: impl Write, value: u64) -> SdkResult<()> {
    writer.write_all(&value.to_le_bytes())?;
    Ok(())
}

fn read_u16(mut reader: impl Read) -> SdkResult<u16> {
    let mut bytes = [0; 2];
    reader.read_exact(&mut bytes)?;
    Ok(u16::from_le_bytes(bytes))
}

fn read_u32(mut reader: impl Read) -> SdkResult<u32> {
    let mut bytes = [0; 4];
    reader.read_exact(&mut bytes)?;
    Ok(u32::from_le_bytes(bytes))
}

fn read_u64(mut reader: impl Read) -> SdkResult<u64> {
    let mut bytes = [0; 8];
    reader.read_exact(&mut bytes)?;
    Ok(u64::from_le_bytes(bytes))
}

#[cfg(test)]
mod tests {
    use super::super::error::SdkErrorCode;
    use super::*;

    #[test]
    fn bundle_round_trips_through_bytes_and_streams() {
        let bundle = sample_bundle();
        let bytes = bundle.to_bundle_bytes().unwrap();
        let loaded = LocationIndexBundle::from_bundle_bytes(&bytes).unwrap();
        assert_eq!(loaded, bundle);
        let mut streamed = Vec::new();
        bundle.write_bundle(&mut streamed).unwrap();
        assert_eq!(
            LocationIndexBundle::read_bundle(Cursor::new(streamed)).unwrap(),
            bundle
        );
    }

    #[test]
    fn derived_index_cache_is_reused_but_never_required() {
        let bundle = sample_bundle();
        let snapshot = bundle.to_bundle_bytes().unwrap();
        let cache = bundle.index_cache_bytes().unwrap();

        let (cached, cache_hit) = LocationIndexBundle::read_bundle_with_index_cache(
            Cursor::new(&snapshot),
            Some(&cache),
            SdkLimits::default(),
        )
        .unwrap();
        assert!(cache_hit);
        assert_eq!(cached, bundle);

        let mut corrupted = cache;
        let last = corrupted.len() - 1;
        corrupted[last] ^= 0x5a;
        let (rebuilt, cache_hit) = LocationIndexBundle::read_bundle_with_index_cache(
            Cursor::new(snapshot),
            Some(&corrupted),
            SdkLimits::default(),
        )
        .unwrap();
        assert!(!cache_hit);
        assert_eq!(rebuilt, bundle);

        let stale_cache = bundle.index_cache_bytes().unwrap();
        let mut changed = bundle.clone();
        changed
            .create_and_apply(DeltaOperation::UpsertFinger {
                template: template("r2", "u2", &[7, 8, 9]),
            })
            .unwrap();
        let (rebuilt, cache_hit) = LocationIndexBundle::read_bundle_with_index_cache(
            Cursor::new(changed.to_bundle_bytes().unwrap()),
            Some(&stale_cache),
            SdkLimits::default(),
        )
        .unwrap();
        assert!(!cache_hit);
        assert_eq!(rebuilt, changed);
    }

    #[test]
    fn low_level_snapshot_parts_rebuild_the_derived_index() {
        let bundle = sample_bundle();
        let parts = bundle.to_binary_parts().unwrap();
        let rebuilt =
            LocationIndexBundle::from_binary_parts(&parts.manifest_json, &parts.templates).unwrap();
        assert_eq!(rebuilt, bundle);
    }

    #[test]
    fn oversized_header_is_rejected_before_allocation() {
        let mut bytes = Vec::from(&BUNDLE_MAGIC[..]);
        bytes.extend_from_slice(&u64::MAX.to_le_bytes());
        bytes.extend_from_slice(&0u64.to_le_bytes());
        let error = LocationIndexBundle::from_bundle_bytes(&bytes).unwrap_err();
        assert_eq!(error.code(), SdkErrorCode::ResourceLimit);
    }

    #[test]
    fn corrupt_section_hash_is_rejected() {
        let mut bytes = sample_bundle().to_bundle_bytes().unwrap();
        let last = bytes.len() - 1;
        bytes[last] ^= 0x55;
        let error = LocationIndexBundle::from_bundle_bytes(&bytes).unwrap_err();
        assert_eq!(error.code(), SdkErrorCode::Integrity);
    }

    #[test]
    fn invalid_delta_does_not_mutate_bundle() {
        let mut bundle = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        let mut invalid = template("r1", "u1", &[1, 2, 3]);
        invalid.token_count = 2;
        let delta = IndexDelta {
            location_id: "school".to_owned(),
            delta_id: "0123456789abcdef0123456789abcdef".to_owned(),
            base_generation: 0,
            sequence: 1,
            operation: DeltaOperation::UpsertFinger { template: invalid },
        };
        let error = bundle.apply_delta(&delta).unwrap_err();
        assert_eq!(error.code(), SdkErrorCode::Integrity);
        assert_eq!(bundle.manifest().generation(), 0);
        assert_eq!(bundle.stats().records, 0);
    }

    #[test]
    fn replay_is_idempotent_but_same_sequence_conflict_is_rejected() {
        let mut source = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        let delta = source
            .create_and_apply(DeltaOperation::UpsertFinger {
                template: template("r1", "u1", &[1, 2, 3]),
            })
            .unwrap();
        let mut replica = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        assert_eq!(
            replica.apply_delta(&delta).unwrap(),
            DeltaApplyStatus::Applied
        );
        assert_eq!(
            replica.apply_delta(&delta).unwrap(),
            DeltaApplyStatus::AlreadyApplied
        );
        let mut conflict = delta.clone();
        conflict.delta_id = "fedcba9876543210fedcba9876543210".to_owned();
        assert_eq!(
            replica.apply_delta(&conflict).unwrap_err().code(),
            SdkErrorCode::Conflict
        );
    }

    #[test]
    fn reused_delta_id_at_another_sequence_is_rejected_without_mutation() {
        let mut bundle = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        let first = bundle
            .create_and_apply(DeltaOperation::UpsertFinger {
                template: template("r1", "u1", &[1, 2, 3]),
            })
            .unwrap();
        let reused = first.rebase(1).unwrap();
        let before = bundle.clone();

        assert_eq!(
            bundle.apply_delta(&reused).unwrap_err().code(),
            SdkErrorCode::Conflict
        );
        assert_eq!(bundle, before);
    }

    #[test]
    fn delta_json_rejects_unknown_fields() {
        let mut bundle = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        let delta = bundle
            .create_and_apply(DeltaOperation::RemoveUser {
                user_id: "u1".to_owned(),
            })
            .unwrap();
        let mut value: serde_json::Value =
            serde_json::from_slice(&delta.to_json_bytes().unwrap()).unwrap();
        value
            .as_object_mut()
            .unwrap()
            .insert("unexpected".to_owned(), serde_json::Value::Bool(true));

        assert_eq!(
            IndexDelta::from_json_bytes(&serde_json::to_vec(&value).unwrap())
                .unwrap_err()
                .code(),
            SdkErrorCode::Serialization
        );
    }

    #[test]
    fn receipt_history_must_be_complete_and_contiguous() {
        let mut bundle = LocationIndexBundle::build("school", TemplateStore::new()).unwrap();
        bundle
            .create_and_apply(DeltaOperation::UpsertFinger {
                template: template("r1", "u1", &[1, 2, 3]),
            })
            .unwrap();
        bundle.manifest.applied_deltas.clear();

        assert_eq!(
            bundle.validate().unwrap_err().code(),
            SdkErrorCode::Integrity
        );
    }

    #[test]
    fn trailing_bundle_bytes_are_rejected() {
        let mut bytes = sample_bundle().to_bundle_bytes().unwrap();
        bytes.push(0);
        assert_eq!(
            LocationIndexBundle::from_bundle_bytes(&bytes)
                .unwrap_err()
                .code(),
            SdkErrorCode::InvalidFormat
        );
    }

    #[test]
    fn representative_bundle_corruption_never_panics() {
        let bytes = sample_bundle().to_bundle_bytes().unwrap();
        let stride = (bytes.len() / 128).max(1);
        for offset in (0..bytes.len()).step_by(stride) {
            let mut corrupted = bytes.clone();
            corrupted[offset] ^= 0x80;
            assert!(
                std::panic::catch_unwind(|| LocationIndexBundle::from_bundle_bytes(&corrupted))
                    .is_ok(),
                "decoder panicked after mutating offset {offset}"
            );
        }
        for length in [0, 1, 7, 8, 31, bytes.len() / 2, bytes.len() - 1] {
            assert!(
                std::panic::catch_unwind(|| LocationIndexBundle::from_bundle_bytes(
                    &bytes[..length]
                ))
                .is_ok(),
                "decoder panicked after truncating to {length} bytes"
            );
        }
    }

    #[test]
    fn long_identifiers_fail_instead_of_writing_corrupt_lengths() {
        let template = template(&"x".repeat(65_536), "u", &[1]);
        let error = TemplateStore::from_templates(vec![template])
            .unwrap()
            .to_bytes()
            .unwrap_err();
        assert!(matches!(
            error.code(),
            SdkErrorCode::InvalidInput | SdkErrorCode::ResourceLimit
        ));
    }

    fn sample_bundle() -> LocationIndexBundle {
        LocationIndexBundle::build(
            "school",
            TemplateStore::from_templates(vec![template("r1", "u1", &[1, 2, 3])]).unwrap(),
        )
        .unwrap()
    }

    fn template(record_id: &str, user_id: &str, tokens: &[u64]) -> ExtractedTemplate {
        ExtractedTemplate {
            record: FingerRecord {
                record_id: record_id.to_owned(),
                user_id: user_id.to_owned(),
            },
            quality: 80,
            token_count: tokens.len() as u16,
            tokens: tokens.to_vec(),
            features: vec![TemplateFeature {
                x: 2,
                y: 3,
                orientation: 4,
                contrast: 4,
                coherence: 80,
                kind: 1,
            }],
        }
    }
}
