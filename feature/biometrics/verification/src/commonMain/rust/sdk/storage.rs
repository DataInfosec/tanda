//! Shared filesystem and identifier helpers.

use sha2::{Digest, Sha256};
use std::fs::{self, File, OpenOptions};
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};
use uuid::Uuid;

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

pub(crate) fn location_storage_key(location_id: &str) -> String {
    hex(&Sha256::digest(location_id.as_bytes()))
}

pub(crate) fn new_operation_id() -> String {
    Uuid::new_v4().simple().to_string()
}

pub(crate) fn checked_next_sequence(current: u64) -> SdkResult<u64> {
    current
        .checked_add(1)
        .ok_or_else(|| SdkError::conflict("sync generation is exhausted"))
}

pub(crate) fn atomic_write(
    path: &Path,
    write: impl FnOnce(&mut BufWriter<File>) -> SdkResult<()>,
) -> SdkResult<()> {
    let parent = path
        .parent()
        .ok_or_else(|| SdkError::invalid_input("persisted path has no parent directory"))?;
    fs::create_dir_all(parent)
        .map_err(|error| SdkError::io(format!("create directory {}", parent.display()), error))?;

    let temporary = unique_temp_path(path);
    let file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&temporary)
        .map_err(|error| {
            SdkError::io(
                format!("create temporary file {}", temporary.display()),
                error,
            )
        })?;
    let mut writer = BufWriter::new(file);
    let result = write(&mut writer).and_then(|_| {
        writer.flush().map_err(|error| {
            SdkError::io(
                format!("flush temporary file {}", temporary.display()),
                error,
            )
        })?;
        writer.get_ref().sync_all().map_err(|error| {
            SdkError::io(
                format!("sync temporary file {}", temporary.display()),
                error,
            )
        })?;
        Ok(())
    });
    if let Err(error) = result {
        drop(writer);
        let _ = fs::remove_file(&temporary);
        return Err(error);
    }
    drop(writer);

    fs::rename(&temporary, path).map_err(|error| {
        let _ = fs::remove_file(&temporary);
        SdkError::io(
            format!("replace {} with {}", path.display(), temporary.display()),
            error,
        )
    })?;
    File::open(parent)
        .and_then(|directory| directory.sync_all())
        .map_err(|error| SdkError::io(format!("sync directory {}", parent.display()), error))?;
    Ok(())
}

pub(crate) fn atomic_write_bytes(path: &Path, bytes: &[u8]) -> SdkResult<()> {
    atomic_write(path, |writer| {
        writer
            .write_all(bytes)
            .map_err(|error| SdkError::io(format!("write {}", path.display()), error))
    })
}

fn unique_temp_path(path: &Path) -> PathBuf {
    let file_name = path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("state");
    path.with_file_name(format!(".{file_name}.{}.tmp", new_operation_id()))
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
