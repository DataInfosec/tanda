//! Resource limits applied before decoded lengths are allocated.

use serde::{Deserialize, Serialize};

use super::error::{SdkError, SdkResult};

/// Limits for persisted data and in-memory location indexes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct SdkLimits {
    /// Maximum complete `.biobundle` size.
    pub max_bundle_bytes: usize,
    /// Maximum manifest JSON section size.
    pub max_manifest_bytes: usize,
    /// Maximum encoded template section size.
    pub max_template_bytes: usize,
    /// Maximum encoded local derived-index cache size.
    pub max_index_bytes: usize,
    /// Maximum finger records in one location.
    pub max_records: usize,
    /// Maximum selected descriptors in one template.
    pub max_tokens_per_template: usize,
    /// Maximum verifier features in one template.
    pub max_features_per_template: usize,
    /// Maximum UTF-8 bytes in any encoded identifier.
    pub max_string_bytes: usize,
    /// Maximum remembered delta receipts in a bundle manifest.
    pub max_delta_history: usize,
}

impl Default for SdkLimits {
    fn default() -> Self {
        Self {
            max_bundle_bytes: 72 * 1024 * 1024,
            max_manifest_bytes: 2 * 1024 * 1024,
            max_template_bytes: 32 * 1024 * 1024,
            max_index_bytes: 32 * 1024 * 1024,
            max_records: 4_096,
            max_tokens_per_template: 1_024,
            max_features_per_template: 512,
            max_string_bytes: 1_024,
            max_delta_history: 10_000,
        }
    }
}

impl SdkLimits {
    /// Validate that the configured limits are internally usable.
    pub fn validate(self) -> SdkResult<Self> {
        if self.max_bundle_bytes == 0
            || self.max_manifest_bytes == 0
            || self.max_template_bytes == 0
            || self.max_index_bytes == 0
            || self.max_records == 0
            || self.max_tokens_per_template == 0
            || self.max_features_per_template == 0
            || self.max_string_bytes == 0
            || self.max_delta_history == 0
        {
            return Err(SdkError::invalid_input(
                "SDK limits must be greater than zero",
            ));
        }
        let section_total = self
            .max_manifest_bytes
            .checked_add(self.max_template_bytes)
            .ok_or_else(|| SdkError::invalid_input("SDK section limits overflow usize"))?;
        if section_total > self.max_bundle_bytes {
            return Err(SdkError::invalid_input(
                "SDK section limits exceed max_bundle_bytes",
            ));
        }
        Ok(self)
    }
}
