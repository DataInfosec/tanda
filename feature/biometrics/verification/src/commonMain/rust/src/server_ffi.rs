//! Panic-contained C ABI for server-side enrollment artifact validation.

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;
use std::slice;
use std::str;

use crate::sdk::{
    EnrollmentConfig, ExtractorConfig, SdkLimits, TemplateArtifactRef, TemplateStore,
    decode_subject_template_artifact, find_cross_subject_duplicate,
};

/// Validation succeeded and the candidate does not duplicate the comparison set.
pub const VALIDATION_ACCEPTED: i32 = 0;
/// Candidate bytes or metadata are invalid and should be rejected.
pub const VALIDATION_INVALID_ARTIFACT: i32 = 1;
/// Candidate matches another subject in the canonical comparison set.
pub const VALIDATION_DUPLICATE: i32 = 2;
/// Adapter input, canonical data, or an internal operation failed.
pub const VALIDATION_INTERNAL_ERROR: i32 = -1;

/// Borrowed artifact fields supplied by the caller for one FFI invocation.
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct BiometricArtifactView {
    /// Opaque SDK payload pointer.
    pub payload: *const u8,
    /// Opaque SDK payload length.
    pub payload_len: usize,
    /// UTF-8 subject identifier pointer.
    pub subject_id: *const u8,
    /// UTF-8 subject identifier length.
    pub subject_id_len: usize,
    /// UTF-8 format version pointer.
    pub format_version: *const u8,
    /// UTF-8 format version length.
    pub format_version_len: usize,
    /// UTF-8 extractor profile pointer.
    pub extractor_profile: *const u8,
    /// UTF-8 extractor profile length.
    pub extractor_profile_len: usize,
    /// UTF-8 checksum pointer.
    pub checksum: *const u8,
    /// UTF-8 checksum length.
    pub checksum_len: usize,
}

/// Rust-owned diagnostic bytes returned to the caller.
#[repr(C)]
#[derive(Debug)]
pub struct BiometricOwnedBytes {
    /// Allocation pointer, or null when no diagnostic was produced.
    pub ptr: *mut u8,
    /// Initialized byte length.
    pub len: usize,
    /// Allocation capacity required by [`biometric_sdk_free_bytes`].
    pub capacity: usize,
}

impl Default for BiometricOwnedBytes {
    fn default() -> Self {
        Self {
            ptr: ptr::null_mut(),
            len: 0,
            capacity: 0,
        }
    }
}

/// Validate one candidate against the caller-selected canonical comparison set.
///
/// The caller retains every input allocation for the duration of this call.
/// A non-empty diagnostic written to `diagnostic` must be released exactly once
/// with [`biometric_sdk_free_bytes`].
///
/// # Safety
///
/// `candidate` must point to one initialized [`BiometricArtifactView`]. When
/// `existing_len` is non-zero, `existing` must point to that many initialized
/// views. Every non-zero field length requires a readable pointer of the same
/// length. `diagnostic`, when non-null, must be writable.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn biometric_sdk_validate_submission(
    candidate: *const BiometricArtifactView,
    existing: *const BiometricArtifactView,
    existing_len: usize,
    diagnostic: *mut BiometricOwnedBytes,
) -> i32 {
    clear_diagnostic(diagnostic);
    let result = catch_unwind(AssertUnwindSafe(|| {
        validate_submission(candidate, existing, existing_len)
    }));
    match result {
        Ok(Ok(code)) => code,
        Ok(Err(error)) => {
            write_diagnostic(diagnostic, error);
            VALIDATION_INTERNAL_ERROR
        }
        Err(_) => {
            write_diagnostic(diagnostic, "biometric SDK validator panicked".to_owned());
            VALIDATION_INTERNAL_ERROR
        }
    }
}

/// Release diagnostic bytes returned by [`biometric_sdk_validate_submission`].
///
/// # Safety
///
/// `bytes` must be either null or a value initialized by this library that has
/// not already been freed.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn biometric_sdk_free_bytes(bytes: *mut BiometricOwnedBytes) {
    if bytes.is_null() {
        return;
    }
    let owned = unsafe { &mut *bytes };
    if !owned.ptr.is_null() {
        let _ = unsafe { Vec::from_raw_parts(owned.ptr, owned.len, owned.capacity) };
    }
    *owned = BiometricOwnedBytes::default();
}

fn validate_submission(
    candidate: *const BiometricArtifactView,
    existing: *const BiometricArtifactView,
    existing_len: usize,
) -> Result<i32, String> {
    if candidate.is_null() {
        return Err("candidate artifact pointer is null".to_owned());
    }
    let extractor = ExtractorConfig::default();
    let limits = SdkLimits::default();
    let enrollment = EnrollmentConfig::default()
        .validate(limits)
        .map_err(|error| format!("default enrollment policy is invalid: {error}"))?;
    let candidate_view = unsafe { &*candidate };
    let candidate_ref = unsafe { decode_view(candidate_view) }?;
    let candidate_store = match decode_subject_template_artifact(candidate_ref, extractor, limits) {
        Ok(store) => store,
        Err(error) => return Ok(invalid_artifact(error.to_string())),
    };

    let existing_views = if existing_len == 0 {
        &[][..]
    } else {
        if existing.is_null() {
            return Err("existing artifact pointer is null".to_owned());
        }
        unsafe { slice::from_raw_parts(existing, existing_len) }
    };
    let mut comparison_set = TemplateStore::new();
    for view in existing_views {
        let artifact = unsafe { decode_view(view) }?;
        let decoded = decode_subject_template_artifact(artifact, extractor, limits)
            .map_err(|error| format!("canonical comparison artifact is invalid: {error}"))?;
        for template in decoded.templates() {
            comparison_set
                .upsert(template)
                .map_err(|error| format!("canonical comparison artifact conflicts: {error}"))?;
        }
    }
    let duplicate = find_cross_subject_duplicate(
        &candidate_store,
        &comparison_set,
        enrollment.duplicate,
        extractor,
        limits,
    )
    .map_err(|error| format!("duplicate check failed: {error}"))?;
    if duplicate.is_some() {
        return Ok(VALIDATION_DUPLICATE);
    }

    Ok(VALIDATION_ACCEPTED)
}

fn invalid_artifact(_message: String) -> i32 {
    VALIDATION_INVALID_ARTIFACT
}

unsafe fn decode_view(view: &BiometricArtifactView) -> Result<TemplateArtifactRef<'_>, String> {
    Ok(TemplateArtifactRef {
        subject_id: unsafe { text_field(view.subject_id, view.subject_id_len, "subject_id")? },
        format_version: unsafe {
            text_field(
                view.format_version,
                view.format_version_len,
                "format_version",
            )?
        },
        extractor_profile: unsafe {
            text_field(
                view.extractor_profile,
                view.extractor_profile_len,
                "extractor_profile",
            )?
        },
        payload: unsafe { byte_field(view.payload, view.payload_len, "payload")? },
        checksum: unsafe { text_field(view.checksum, view.checksum_len, "checksum")? },
    })
}

unsafe fn text_field<'a>(pointer: *const u8, len: usize, name: &str) -> Result<&'a str, String> {
    let bytes = unsafe { byte_field(pointer, len, name) }?;
    str::from_utf8(bytes).map_err(|_| format!("{name} is not UTF-8"))
}

unsafe fn byte_field<'a>(pointer: *const u8, len: usize, name: &str) -> Result<&'a [u8], String> {
    if len == 0 {
        return Err(format!("{name} is empty"));
    }
    if pointer.is_null() {
        return Err(format!("{name} pointer is null"));
    }
    Ok(unsafe { slice::from_raw_parts(pointer, len) })
}

fn clear_diagnostic(diagnostic: *mut BiometricOwnedBytes) {
    if !diagnostic.is_null() {
        unsafe { *diagnostic = BiometricOwnedBytes::default() };
    }
}

fn write_diagnostic(diagnostic: *mut BiometricOwnedBytes, message: String) {
    if diagnostic.is_null() {
        return;
    }
    let mut bytes = message.into_bytes();
    let owned = BiometricOwnedBytes {
        ptr: bytes.as_mut_ptr(),
        len: bytes.len(),
        capacity: bytes.capacity(),
    };
    std::mem::forget(bytes);
    unsafe { *diagnostic = owned };
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::sdk::{
        DEFAULT_EXTRACTOR_PROFILE, ExtractedTemplate, FingerRecord, TEMPLATE_FORMAT_VERSION,
        TemplateFeature, template_payload_checksum,
    };

    fn artifact(subject_id: &str) -> (Vec<u8>, String) {
        let store = TemplateStore::from_templates(vec![ExtractedTemplate {
            record: FingerRecord {
                record_id: "record-1".to_owned(),
                user_id: subject_id.to_owned(),
            },
            quality: 80,
            token_count: 1,
            tokens: vec![12],
            features: vec![TemplateFeature {
                x: 10,
                y: 10,
                orientation: 1,
                contrast: 3,
                coherence: 80,
                kind: 1,
            }],
        }])
        .unwrap();
        let payload = store.to_bytes().unwrap();
        let checksum = template_payload_checksum(&payload);
        (payload, checksum)
    }

    fn view<'a>(
        subject_id: &'a str,
        payload: &'a [u8],
        checksum: &'a str,
    ) -> BiometricArtifactView {
        BiometricArtifactView {
            payload: payload.as_ptr(),
            payload_len: payload.len(),
            subject_id: subject_id.as_ptr(),
            subject_id_len: subject_id.len(),
            format_version: TEMPLATE_FORMAT_VERSION.as_ptr(),
            format_version_len: TEMPLATE_FORMAT_VERSION.len(),
            extractor_profile: DEFAULT_EXTRACTOR_PROFILE.as_ptr(),
            extractor_profile_len: DEFAULT_EXTRACTOR_PROFILE.len(),
            checksum: checksum.as_ptr(),
            checksum_len: checksum.len(),
        }
    }

    #[test]
    fn ffi_accepts_a_valid_one_subject_artifact() {
        let (payload, checksum) = artifact("subject-1");
        let candidate = view("subject-1", &payload, &checksum);
        let mut diagnostic = BiometricOwnedBytes::default();
        let code = unsafe {
            biometric_sdk_validate_submission(&candidate, ptr::null(), 0, &mut diagnostic)
        };
        assert_eq!(code, VALIDATION_ACCEPTED);
        assert!(diagnostic.ptr.is_null());
    }

    #[test]
    fn ffi_classifies_a_candidate_checksum_failure_as_rejection() {
        let (payload, _) = artifact("subject-1");
        let candidate = view("subject-1", &payload, "sha256:wrong");
        let code = unsafe {
            biometric_sdk_validate_submission(&candidate, ptr::null(), 0, ptr::null_mut())
        };
        assert_eq!(code, VALIDATION_INVALID_ARTIFACT);
    }
}
