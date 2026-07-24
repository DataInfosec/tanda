//! Biometric matching, artifact validation, and SDK-owned campus persistence.
//!
//! [`CampusBiometricSdk`](crate::sdk::CampusBiometricSdk) is the application entry point when the
//! `campus-libsql` feature is enabled. The app supplies device provisioning and
//! raw captures; Rust owns the class gallery, enrollment queue, synchronization,
//! and immutable in-memory matcher. [`BiometricIndex`](crate::sdk::BiometricIndex)
//! and [`TemplateStore`](crate::sdk::TemplateStore)
//! remain available for server adapters and specialized Rust integrations.

mod artifact;
#[cfg(feature = "campus-libsql")]
mod campus;
mod enrollment;
mod error;
mod extractor;
mod gallery;
mod index;
mod limits;
mod persist;
mod storage;
mod template;

pub use artifact::{
    TemplateArtifactRef, decode_student_template_artifact, find_school_duplicate,
    template_payload_checksum,
};
#[cfg(feature = "campus-libsql")]
pub use campus::{
    CampusBiometricSdk, CampusConfig, CampusEnrollmentResult, CampusProvisioning, CampusSyncReport,
    CampusSyncState, EnrollmentBatch,
};
pub use enrollment::{
    DEFAULT_ENROLLMENT_MIN_QUALITY, DuplicateCheckConfig, DuplicateEnrollmentMatch,
    EnrollmentAttempt, EnrollmentConfig, EnrollmentRejectionReason, EnrollmentReport,
};
pub use error::{SdkError, SdkErrorCode, SdkResult};
pub use extractor::{
    ExtractedTemplate, ExtractorConfig, FingerRecord, TemplateFeature, extract_raw_bytes,
};
pub use gallery::{GalleryIndex, GalleryStats};
pub use index::{
    BiometricIndex, IdentifyConfig, IdentifyMatch, IdentifyResult, IdentifyRetry,
    IdentifyRetryReason, IndexStats, RecordSearchHit, RerankConfig, SearchConfig, SearchHit,
};
pub use limits::SdkLimits;
pub use template::{DEFAULT_EXTRACTOR_PROFILE, TEMPLATE_FORMAT_VERSION, TemplateStore};
