#![warn(missing_docs)]

//! Pure Rust biometric SDK for local, per-location fingerprint enrollment,
//! bundle sync, and offline 1:N clock-in.
//!
//! The crate is split into two layers:
//!
//! - [`fingerprint`]: raw 400x500 grayscale capture validation and orientation.
//! - [`sdk`]: extraction, indexing, matching, persistence, and location sync
//!   bundles.
//!
//! Most app integrations should use the re-exported types under [`sdk`],
//! especially `LocationIndexBundle` for one-file `.biobundle` snapshots and
//! stream-based Android/Kotlin Multiplatform integration.
uniffi::setup_scaffolding!();

/// Raw 400x500 grayscale capture handling and image QA helpers.
pub mod fingerprint;

/// UniFFI facade designed for Kotlin Multiplatform's Android source set.
#[cfg(feature = "kotlin-bindings")]
pub mod kotlin;

/// SDK-facing extraction, indexing, matching, persistence, and sync APIs.
pub mod sdk;
