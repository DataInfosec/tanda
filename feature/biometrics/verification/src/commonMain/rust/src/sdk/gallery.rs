//! Immutable, searchable biometric gallery state.
//!
//! [`GalleryIndex`] is derived from current libSQL rows. It has no snapshot
//! framing, synchronization receipts, or filesystem ownership. Callers build a
//! replacement off the matching path and publish it atomically when every
//! template has been decoded and indexed successfully.

use super::error::{SdkError, SdkResult};
use super::extractor::{ExtractedTemplate, ExtractorConfig, extract_raw_bytes};
use super::index::{BiometricIndex, IdentifyConfig, IdentifyResult, SearchConfig, SearchHit};
use super::limits::SdkLimits;
use super::storage::validate_identifier;
use super::template::TemplateStore;

/// Summary of one immutable matcher generation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GalleryStats {
    /// Server-authoritative gallery identifier.
    pub gallery_id: String,
    /// Canonical application revision represented by this matcher.
    pub gallery_revision: u64,
    /// Number of indexed finger records.
    pub records: usize,
    /// Number of distinct gallery subjects.
    pub subjects: usize,
}

/// Validated current gallery and its derived search index.
#[derive(Debug, Clone, PartialEq)]
pub struct GalleryIndex {
    gallery_id: String,
    gallery_revision: u64,
    extractor: ExtractorConfig,
    identify: IdentifyConfig,
    store: TemplateStore,
    index: BiometricIndex,
    limits: SdkLimits,
}

impl GalleryIndex {
    /// Build current state with the SDK's default profiles and limits.
    pub fn build(
        gallery_id: impl Into<String>,
        gallery_revision: u64,
        store: TemplateStore,
    ) -> SdkResult<Self> {
        Self::build_with_profiles(
            gallery_id,
            gallery_revision,
            store,
            ExtractorConfig::default(),
            IdentifyConfig::default(),
            SdkLimits::default(),
        )
    }

    /// Build current state with explicit extraction, matching, and size policy.
    pub fn build_with_profiles(
        gallery_id: impl Into<String>,
        gallery_revision: u64,
        store: TemplateStore,
        extractor: ExtractorConfig,
        identify: IdentifyConfig,
        limits: SdkLimits,
    ) -> SdkResult<Self> {
        let gallery_id = gallery_id.into();
        validate_identifier("gallery_id", &gallery_id)?;
        let limits = limits.validate()?;
        let extractor = extractor.validate(limits)?;
        let index = BiometricIndex::build_with_config(&store.templates(), extractor, limits)?;
        Ok(Self {
            gallery_id,
            gallery_revision,
            extractor,
            identify,
            store,
            index,
            limits,
        })
    }

    /// Server-authoritative gallery identifier.
    pub fn gallery_id(&self) -> &str {
        &self.gallery_id
    }

    /// Canonical application revision represented by this index.
    pub fn gallery_revision(&self) -> u64 {
        self.gallery_revision
    }

    /// Extraction profile required by every stored artifact and query.
    pub fn extractor_config(&self) -> ExtractorConfig {
        self.extractor
    }

    /// Identification policy applied by [`GalleryIndex::identify_raw_bytes`].
    pub fn identify_config(&self) -> IdentifyConfig {
        self.identify
    }

    /// Return stable template order for duplicate checks and rebuilt state.
    pub fn templates(&self) -> Vec<ExtractedTemplate> {
        self.store.templates()
    }

    /// Return the current matcher size.
    pub fn stats(&self) -> GalleryStats {
        GalleryStats {
            gallery_id: self.gallery_id.clone(),
            gallery_revision: self.gallery_revision,
            records: self.store.len(),
            subjects: self.store.user_count(),
        }
    }

    /// Search a raw query without applying the final identity decision policy.
    pub fn search_raw_bytes(&self, raw: &[u8], config: SearchConfig) -> SdkResult<Vec<SearchHit>> {
        let query = extract_raw_bytes("query", "query", raw, self.extractor)?;
        self.index.search_users(&query, config)
    }

    /// Identify a subject using the configured gallery policy.
    pub fn identify_raw_bytes(&self, raw: &[u8]) -> SdkResult<IdentifyResult> {
        self.identify_raw_bytes_with_config(raw, self.identify)
    }

    /// Identify a user using an explicit policy and the gallery extractor.
    pub fn identify_raw_bytes_with_config(
        &self,
        raw: &[u8],
        config: IdentifyConfig,
    ) -> SdkResult<IdentifyResult> {
        let query = extract_raw_bytes("query", "query", raw, self.extractor)?;
        self.index.identify_user(&query, config)
    }

    /// Build replacement state for one user without mutating this index.
    pub fn replacing_user(
        &self,
        gallery_revision: u64,
        user_id: &str,
        replacement: TemplateStore,
    ) -> SdkResult<Self> {
        validate_identifier("user_id", user_id)?;
        if replacement.single_user_id()? != user_id {
            return Err(SdkError::conflict(
                "replacement artifact belongs to another user",
            ));
        }
        let mut store = self.store.clone();
        store.remove_user(user_id);
        for template in replacement.templates() {
            store.upsert(template)?;
        }
        Self::build_with_profiles(
            self.gallery_id.clone(),
            gallery_revision,
            store,
            self.extractor,
            self.identify,
            self.limits,
        )
    }

    /// Build state with one user removed without mutating this index.
    pub fn removing_user(&self, gallery_revision: u64, user_id: &str) -> SdkResult<Self> {
        validate_identifier("user_id", user_id)?;
        let mut store = self.store.clone();
        store.remove_user(user_id);
        Self::build_with_profiles(
            self.gallery_id.clone(),
            gallery_revision,
            store,
            self.extractor,
            self.identify,
            self.limits,
        )
    }
}

#[cfg(test)]
mod tests {
    use super::super::extractor::{FingerRecord, TemplateFeature};
    use super::*;

    fn template(record_id: &str, user_id: &str, token: u64) -> ExtractedTemplate {
        ExtractedTemplate {
            record: FingerRecord {
                record_id: record_id.to_owned(),
                user_id: user_id.to_owned(),
            },
            quality: 80,
            token_count: 1,
            tokens: vec![token],
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
    fn replacement_builds_new_state_without_mutating_current_gallery() {
        let original = GalleryIndex::build(
            "population:site:period:a",
            1,
            TemplateStore::from_templates(vec![template("r1", "subject-1", 10)]).unwrap(),
        )
        .unwrap();
        let replacement =
            TemplateStore::from_templates(vec![template("r2", "subject-1", 20)]).unwrap();

        let updated = original
            .replacing_user(2, "subject-1", replacement)
            .unwrap();

        assert_eq!(original.gallery_revision(), 1);
        assert_eq!(original.templates()[0].record.record_id, "r1");
        assert_eq!(updated.gallery_revision(), 2);
        assert_eq!(updated.templates()[0].record.record_id, "r2");
    }
}
