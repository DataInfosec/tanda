//! Tanda's native packaging bridge for the shared biometric SDK.
//!
//! Biometric behavior and the Kotlin-facing API live in the standalone
//! `DataInfosec/biometric-sdk` repository. This crate pins that implementation
//! and gives Gobley one native library to build and package for Tanda.

pub use biometric_sdk_core::kotlin::*;
