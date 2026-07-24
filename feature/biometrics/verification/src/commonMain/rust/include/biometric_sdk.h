#ifndef BIOMETRIC_SDK_H
#define BIOMETRIC_SDK_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Borrowed fields for one one-subject fingerprint template artifact.
 *
 * Every pointer remains owned by the caller. A non-zero length requires a
 * readable pointer for the duration of biometric_sdk_validate_submission().
 * Text fields are UTF-8 and do not need a trailing NUL byte.
 */
typedef struct BiometricArtifactView {
    const uint8_t *payload;
    size_t payload_len;
    const uint8_t *subject_id;
    size_t subject_id_len;
    const uint8_t *format_version;
    size_t format_version_len;
    const uint8_t *extractor_profile;
    size_t extractor_profile_len;
    const uint8_t *checksum;
    size_t checksum_len;
} BiometricArtifactView;

/** Rust-owned diagnostic bytes returned for an internal validator failure. */
typedef struct BiometricOwnedBytes {
    uint8_t *ptr;
    size_t len;
    size_t capacity;
} BiometricOwnedBytes;

/** Stable result codes returned by biometric_sdk_validate_submission(). */
enum BiometricValidationCode {
    BIOMETRIC_VALIDATION_INTERNAL_ERROR = -1,
    BIOMETRIC_VALIDATION_ACCEPTED = 0,
    BIOMETRIC_VALIDATION_INVALID_ARTIFACT = 1,
    BIOMETRIC_VALIDATION_DUPLICATE = 2
};

/**
 * Validate a candidate artifact against the caller-selected uniqueness set.
 *
 * Existing artifacts are canonical templates for the same modality. The
 * caller owns the comparison scope. Expected enrollment rejections are
 * returned as positive result codes. Adapter failures return
 * BIOMETRIC_VALIDATION_INTERNAL_ERROR and may allocate diagnostic bytes when
 * diagnostic is non-NULL.
 */
int32_t biometric_sdk_validate_submission(
    const BiometricArtifactView *candidate,
    const BiometricArtifactView *existing,
    size_t existing_len,
    BiometricOwnedBytes *diagnostic
);

/**
 * Release diagnostic bytes initialized by biometric_sdk_validate_submission().
 * Passing NULL or an empty BiometricOwnedBytes value is valid.
 */
void biometric_sdk_free_bytes(BiometricOwnedBytes *bytes);

#ifdef __cplusplus
}
#endif

#endif
