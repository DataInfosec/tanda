//! Resource limits applied before decoded lengths are allocated.

use serde::{Deserialize, Serialize};

use super::error::{SdkError, SdkResult};

/// Limits for template artifacts and in-memory class indexes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct SdkLimits {
    /// Maximum encoded one-student template artifact size.
    pub max_template_bytes: usize,
    /// Maximum encoded derived-index size.
    pub max_index_bytes: usize,
    /// Maximum finger records in one class gallery.
    pub max_records: usize,
    /// Maximum selected descriptors in one template.
    pub max_tokens_per_template: usize,
    /// Maximum verifier features in one template.
    pub max_features_per_template: usize,
    /// Maximum UTF-8 bytes in any encoded identifier.
    pub max_string_bytes: usize,
}

impl Default for SdkLimits {
    fn default() -> Self {
        Self {
            max_template_bytes: 32 * 1024 * 1024,
            max_index_bytes: 32 * 1024 * 1024,
            max_records: 4_096,
            max_tokens_per_template: 1_024,
            max_features_per_template: 512,
            max_string_bytes: 1_024,
        }
    }
}

impl SdkLimits {
    /// Validate that the configured limits are internally usable.
    pub fn validate(self) -> SdkResult<Self> {
        if self.max_template_bytes == 0
            || self.max_index_bytes == 0
            || self.max_records == 0
            || self.max_tokens_per_template == 0
            || self.max_features_per_template == 0
            || self.max_string_bytes == 0
        {
            return Err(SdkError::invalid_input(
                "SDK limits must be greater than zero",
            ));
        }
        Ok(self)
    }
}
