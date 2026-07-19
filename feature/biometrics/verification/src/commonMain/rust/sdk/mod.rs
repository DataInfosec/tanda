//! Public SDK facade.
//!
//! Internal modules are kept small by ownership:
//!
//! - `extractor`: raw capture to extracted template.
//! - `index`: in-memory index, candidate search, and template verifier.
//! - `persist`: compact index binary format.
//! - `bundle`: per-location snapshots and deltas.
//! - `enrollment`: filesystem-backed initial enrollment sessions and future
//!   user enrollment.
//!
//! Types re-exported here are the intended surface for app integrations and
//! Kotlin Multiplatform bindings. Apps should normally load one
//! `LocationIndexBundle` per school/class/location, call `identify_raw_bytes`
//! for clock-ins, apply ordered `IndexDelta` payloads from sync, and export the
//! updated bundle through a platform-provided stream.

mod bundle;
mod enrollment;
mod error;
mod extractor;
mod index;
mod limits;
mod persist;
mod storage;

pub use bundle::{
    AppliedDeltaReceipt, BundleStats, DeltaApplyStatus, DeltaOperation, IndexDelta,
    LocationBundleBytes, LocationIndexBundle, LocationManifest, SyncSequence, TemplateStore,
};
pub use enrollment::{
    BiometricSdk, DEFAULT_ENROLLMENT_MIN_QUALITY, DuplicateCheckConfig, DuplicateEnrollmentMatch,
    EnrollmentAttempt, EnrollmentCloseResult, EnrollmentConfig, EnrollmentDeltaResult,
    EnrollmentRejectionReason, EnrollmentReport, EnrollmentSession, EnrollmentSessionSummary,
    SdkConfig,
};
pub use error::{SdkError, SdkErrorCode, SdkResult};
pub use extractor::{
    ExtractedTemplate, ExtractorConfig, FingerRecord, TemplateFeature, extract_raw_bytes,
};
pub use index::{
    BiometricIndex, IdentifyConfig, IdentifyMatch, IdentifyResult, IdentifyRetry,
    IdentifyRetryReason, IndexStats, RecordSearchHit, RerankConfig, SearchConfig, SearchHit,
};
pub use limits::SdkLimits;
