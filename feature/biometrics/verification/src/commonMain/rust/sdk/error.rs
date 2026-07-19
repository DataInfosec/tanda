//! Stable SDK error categories.
//!
//! App bindings should branch on [`SdkErrorCode`] and treat the human-readable
//! message as diagnostic context only. This keeps Kotlin/Android integrations
//! independent of Rust error strings.

use std::error::Error;
use std::fmt::{self, Display, Formatter};
use std::io;

use serde::{Deserialize, Serialize};

/// Stable category for an SDK failure.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum SdkErrorCode {
    /// Caller supplied an invalid identifier, capture, or configuration.
    InvalidInput,
    /// Encoded data is malformed or unsupported.
    InvalidFormat,
    /// Encoded data failed an integrity or consistency check.
    Integrity,
    /// Local or synchronized state conflicts with the requested operation.
    Conflict,
    /// Requested persisted state does not exist.
    NotFound,
    /// An exclusive enrollment session is already owned elsewhere.
    SessionActive,
    /// Filesystem or stream I/O failed.
    Io,
    /// JSON encoding or decoding failed.
    Serialization,
    /// Memory requested by decoded input could not be reserved safely.
    ResourceLimit,
}

/// Error returned by public SDK operations.
#[derive(Debug)]
pub struct SdkError {
    code: SdkErrorCode,
    message: String,
    source: Option<Box<dyn Error + Send + Sync>>,
}

impl SdkError {
    /// Construct an error with a stable code and diagnostic message.
    pub fn new(code: SdkErrorCode, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
            source: None,
        }
    }

    /// Return the stable category for this failure.
    pub fn code(&self) -> SdkErrorCode {
        self.code
    }

    /// Invalid caller input.
    pub(crate) fn invalid_input(message: impl Into<String>) -> Self {
        Self::new(SdkErrorCode::InvalidInput, message)
    }

    /// Malformed encoded data.
    pub(crate) fn invalid_format(message: impl Into<String>) -> Self {
        Self::new(SdkErrorCode::InvalidFormat, message)
    }

    /// Integrity or cross-section consistency failure.
    pub(crate) fn integrity(message: impl Into<String>) -> Self {
        Self::new(SdkErrorCode::Integrity, message)
    }

    /// State or synchronization conflict.
    pub(crate) fn conflict(message: impl Into<String>) -> Self {
        Self::new(SdkErrorCode::Conflict, message)
    }

    /// Missing persisted object.
    pub(crate) fn not_found(message: impl Into<String>) -> Self {
        Self::new(SdkErrorCode::NotFound, message)
    }

    /// Another owner holds the enrollment lease.
    pub(crate) fn session_active(message: impl Into<String>) -> Self {
        Self::new(SdkErrorCode::SessionActive, message)
    }

    /// I/O failure with operation context.
    pub(crate) fn io(message: impl Into<String>, source: io::Error) -> Self {
        Self {
            code: SdkErrorCode::Io,
            message: message.into(),
            source: Some(Box::new(source)),
        }
    }

    /// JSON failure with operation context.
    pub(crate) fn serialization(message: impl Into<String>, source: serde_json::Error) -> Self {
        Self {
            code: SdkErrorCode::Serialization,
            message: message.into(),
            source: Some(Box::new(source)),
        }
    }

    /// Resource request exceeded a configured or platform limit.
    pub(crate) fn resource_limit(message: impl Into<String>) -> Self {
        Self::new(SdkErrorCode::ResourceLimit, message)
    }
}

impl Display for SdkError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.message)
    }
}

impl Error for SdkError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        self.source
            .as_deref()
            .map(|source| source as &(dyn Error + 'static))
    }
}

impl From<io::Error> for SdkError {
    fn from(source: io::Error) -> Self {
        Self::io("SDK I/O failed", source)
    }
}

impl From<serde_json::Error> for SdkError {
    fn from(source: serde_json::Error) -> Self {
        Self::serialization("SDK JSON operation failed", source)
    }
}

/// Result type returned by public SDK operations.
pub type SdkResult<T> = Result<T, SdkError>;
