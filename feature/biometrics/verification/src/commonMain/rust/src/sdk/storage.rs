//! Shared identifier and checksum text helpers.

use super::error::{SdkError, SdkResult};

const MAX_IDENTIFIER_BYTES: usize = 256;

pub(crate) fn validate_identifier(label: &str, value: &str) -> SdkResult<()> {
    if value.trim().is_empty() {
        return Err(SdkError::invalid_input(format!("{label} is required")));
    }
    if value.len() > MAX_IDENTIFIER_BYTES {
        return Err(SdkError::invalid_input(format!(
            "{label} exceeds {MAX_IDENTIFIER_BYTES} UTF-8 bytes"
        )));
    }
    if value.chars().any(char::is_control) {
        return Err(SdkError::invalid_input(format!(
            "{label} contains control characters"
        )));
    }
    Ok(())
}

pub(crate) fn hex(bytes: &[u8]) -> String {
    const DIGITS: &[u8; 16] = b"0123456789abcdef";
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        output.push(DIGITS[(byte >> 4) as usize] as char);
        output.push(DIGITS[(byte & 0x0f) as usize] as char);
    }
    output
}
