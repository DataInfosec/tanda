#![warn(missing_docs)]

//! Pure Rust biometric SDK for synchronized subject enrollment and offline 1:N
//! fingerprint matching.
//!
//! The crate is split into two layers:
//!
//! - [`fingerprint`]: raw 400x500 grayscale capture validation and orientation.
//! - [`sdk`]: extraction, indexing, enrollment artifacts, SDK-owned libSQL
//!   persistence, and remote synchronization.
//!
//! Foreign-language integrations generate bindings from the UniFFI metadata
//! exposed by [`kotlin::MobileBiometricSdk`]. Rust applications use
//! [`sdk::AttendanceBiometricSdk`] when the `attendance-libsql` feature is enabled.

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();

/// Raw 400x500 grayscale capture handling and image QA helpers.
pub mod fingerprint;

/// Stable C ABI used by the Tanda Campus Go server adapter.
#[cfg(feature = "server-ffi")]
pub mod server_ffi;

/// UniFFI facade consumed by the mobile team's binding generator.
#[cfg(feature = "uniffi-bindings")]
pub mod kotlin;

/// SDK-facing extraction, indexing, matching, enrollment, and sync APIs.
pub mod sdk;
