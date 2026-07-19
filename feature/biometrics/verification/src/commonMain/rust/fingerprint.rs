//! Raw fingerprint image boundary.
//!
//! The SDK currently assumes 400x500 8-bit grayscale captures. This module keeps
//! that sensor assumption in one place and exposes oriented pixels for
//! extraction. It deliberately does not know about matching, templates, users,
//! or sync.

use crate::sdk::{SdkError, SdkResult};

/// Expected raw capture width in pixels.
pub const RAW_WIDTH: u32 = 400;

/// Expected raw capture height in pixels.
pub const RAW_HEIGHT: u32 = 500;

/// Expected raw capture size in bytes for an 8-bit grayscale image.
pub const RAW_LEN: usize = (RAW_WIDTH as usize) * (RAW_HEIGHT as usize);

/// Fixed-size 400x500 8-bit grayscale fingerprint capture.
#[derive(Debug, Clone)]
pub struct RawFingerprint {
    pixels: Vec<u8>,
}

impl RawFingerprint {
    /// Decode a raw 400x500 8-bit grayscale capture from bytes.
    ///
    /// The input must be exactly [`RAW_LEN`] bytes. Orientation is not changed at
    /// this boundary; callers choose orientation when asking for pixels or PNGs.
    pub fn from_bytes(bytes: &[u8]) -> SdkResult<Self> {
        if bytes.len() != RAW_LEN {
            return Err(SdkError::new(
                crate::sdk::SdkErrorCode::InvalidInput,
                format!(
                    "unexpected raw fingerprint size: got {} bytes, expected {}",
                    bytes.len(),
                    RAW_LEN
                ),
            ));
        }
        Ok(Self {
            pixels: bytes.to_vec(),
        })
    }

    /// Return oriented grayscale pixels for extraction.
    ///
    /// This allocates a `Vec<u8>` in display/matcher order.
    pub fn oriented_pixels(&self, flip_vertical: bool) -> Vec<u8> {
        let mut pixels = vec![0; RAW_LEN];
        for y in 0..RAW_HEIGHT {
            let source_y = if flip_vertical { RAW_HEIGHT - 1 - y } else { y };
            let source_start = (source_y * RAW_WIDTH) as usize;
            let target_start = (y * RAW_WIDTH) as usize;
            pixels[target_start..target_start + RAW_WIDTH as usize]
                .copy_from_slice(&self.pixels[source_start..source_start + RAW_WIDTH as usize]);
        }
        pixels
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn flips_raw_storage_vertically() {
        let mut pixels = [0; RAW_LEN];
        pixels[0] = 10;
        pixels[(RAW_HEIGHT as usize - 1) * RAW_WIDTH as usize] = 200;

        let raw = RawFingerprint {
            pixels: pixels.to_vec(),
        };
        let oriented = raw.oriented_pixels(true);

        assert_eq!(oriented[0], 200);
        assert_eq!(oriented[(RAW_HEIGHT as usize - 1) * RAW_WIDTH as usize], 10);
    }
}
