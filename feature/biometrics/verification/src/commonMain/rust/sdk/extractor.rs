//! Fingerprint template extraction.
//!
//! Candidate descriptors encode local ridge relationships rather than absolute
//! image coordinates. A translated or modestly rotated impression therefore
//! retains descriptors that can reach the geometric verifier.
//!
//! Extraction produces two representations of the same filtered minutiae:
//!
//! - sorted pair/triplet descriptor hashes for fast inverted-index retrieval;
//! - spatially distributed minutiae for second-stage geometric verification.
//!
//! The main stages are ridge-field estimation ([`block_features`]),
//! half-resolution skeleton construction, crossing-number minutiae detection
//! ([`minutiae_features`]), invariant descriptor construction
//! ([`tokens_from_features`]), and capture scoring
//! ([`quality_from_features`]). The repository's `docs/matcher-algorithm.md`
//! explains the equations, coordinate units, defaults, and tradeoffs.

use std::cell::RefCell;
use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::fingerprint::{RAW_HEIGHT, RAW_LEN, RAW_WIDTH};

use super::error::{SdkError, SdkResult};
use super::limits::SdkLimits;
use super::storage::validate_identifier;

/// Full-resolution side length of one ridge-field block.
pub(crate) const BLOCK: u32 = 20;
/// Number of ridge-field blocks across the image.
pub(crate) const GRID_W: u32 = RAW_WIDTH / BLOCK;
/// Number of ridge-field blocks down the image.
pub(crate) const GRID_H: u32 = RAW_HEIGHT / BLOCK;
/// Width of the half-resolution minutiae coordinate system.
pub(crate) const FEATURE_GRID_W: u32 = RAW_WIDTH / 2;
/// Height of the half-resolution minutiae coordinate system.
pub(crate) const FEATURE_GRID_H: u32 = RAW_HEIGHT / 2;

/// Tunable parameters for converting raw captures into templates.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ExtractorConfig {
    /// Maximum selected pair/triplet descriptor hashes retained per finger.
    pub max_tokens: usize,
    /// Minimum block pixel standard deviation.
    pub min_block_stddev: f32,
    /// Minimum block gradient energy.
    pub min_block_energy: f32,
    /// Minimum directional coherence in the range `0.0..=1.0`.
    pub min_block_coherence: f32,
    /// Number of ridge-orientation bins over 180 degrees.
    pub orientation_bins: u8,
    /// Nearest minutiae described around each anchor.
    pub max_neighbors: usize,
    /// Spatially distributed minutiae retained for geometric verification.
    pub max_verifier_features: usize,
}

impl Default for ExtractorConfig {
    fn default() -> Self {
        Self {
            max_tokens: 512,
            min_block_stddev: 4.0,
            min_block_energy: 20.0,
            min_block_coherence: 0.18,
            orientation_bins: 16,
            max_neighbors: 6,
            max_verifier_features: 64,
        }
    }
}

impl ExtractorConfig {
    /// Validate extractor settings before processing a capture.
    pub fn validate(self, limits: SdkLimits) -> SdkResult<Self> {
        if self.max_tokens == 0 || self.max_tokens > limits.max_tokens_per_template {
            return Err(SdkError::invalid_input(format!(
                "max_tokens must be in 1..={}",
                limits.max_tokens_per_template
            )));
        }
        if !self.min_block_stddev.is_finite() || self.min_block_stddev < 0.0 {
            return Err(SdkError::invalid_input(
                "min_block_stddev must be finite and non-negative",
            ));
        }
        if !self.min_block_energy.is_finite() || self.min_block_energy < 0.0 {
            return Err(SdkError::invalid_input(
                "min_block_energy must be finite and non-negative",
            ));
        }
        if !self.min_block_coherence.is_finite() || !(0.0..=1.0).contains(&self.min_block_coherence)
        {
            return Err(SdkError::invalid_input(
                "min_block_coherence must be in 0.0..=1.0",
            ));
        }
        if !(8..=32).contains(&self.orientation_bins) || !self.orientation_bins.is_multiple_of(2) {
            return Err(SdkError::invalid_input(
                "orientation_bins must be an even value in 8..=32",
            ));
        }
        if !(2..=16).contains(&self.max_neighbors) {
            return Err(SdkError::invalid_input("max_neighbors must be in 2..=16"));
        }
        if self.max_verifier_features < 16
            || self.max_verifier_features > limits.max_features_per_template
        {
            return Err(SdkError::invalid_input(format!(
                "max_verifier_features must be in 16..={}",
                limits.max_features_per_template
            )));
        }
        Ok(self)
    }
}

/// Metadata identifying one enrolled finger record.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct FingerRecord {
    /// Stable SDK-generated finger record identifier.
    pub record_id: String,
    /// Stable application user identifier.
    pub user_id: String,
}

/// Compact template derived from one fingerprint capture.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ExtractedTemplate {
    /// Finger-record metadata.
    pub record: FingerRecord,
    /// Enrollment quality score in `0..=100`.
    pub quality: u8,
    /// Number of selected candidate descriptors.
    pub token_count: u16,
    /// Sorted translation/rotation-invariant descriptor hashes.
    pub tokens: Vec<u64>,
    /// Compact ridge features used by geometric verification.
    pub features: Vec<TemplateFeature>,
}

impl ExtractedTemplate {
    /// Validate a template before indexing, synchronization, or persistence.
    pub fn validate(&self, config: ExtractorConfig, limits: SdkLimits) -> SdkResult<()> {
        validate_identifier("record_id", &self.record.record_id)?;
        validate_identifier("user_id", &self.record.user_id)?;
        if self.tokens.len() > limits.max_tokens_per_template {
            return Err(SdkError::resource_limit("template token limit exceeded"));
        }
        if self.features.len() > limits.max_features_per_template {
            return Err(SdkError::resource_limit("template feature limit exceeded"));
        }
        if usize::from(self.token_count) != self.tokens.len() {
            return Err(SdkError::integrity(format!(
                "template token count mismatch for {}",
                self.record.record_id
            )));
        }
        if self.tokens.windows(2).any(|tokens| tokens[0] >= tokens[1]) {
            return Err(SdkError::integrity(format!(
                "template tokens are not strictly sorted for {}",
                self.record.record_id
            )));
        }
        for feature in &self.features {
            if u32::from(feature.x) >= FEATURE_GRID_W || u32::from(feature.y) >= FEATURE_GRID_H {
                return Err(SdkError::integrity(format!(
                    "template feature coordinate is outside the extractor grid for {}",
                    self.record.record_id
                )));
            }
            if feature.orientation >= config.orientation_bins {
                return Err(SdkError::integrity(format!(
                    "template orientation exceeds extractor bins for {}",
                    self.record.record_id
                )));
            }
            if feature.contrast > 7 || feature.coherence > 100 || !matches!(feature.kind, 1 | 3) {
                return Err(SdkError::integrity(format!(
                    "template feature payload is invalid for {}",
                    self.record.record_id
                )));
            }
        }
        Ok(())
    }
}

/// Local ridge feature used by second-stage verification.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct TemplateFeature {
    /// X-coordinate on the half-resolution minutiae grid (two source pixels).
    pub x: u8,
    /// Y-coordinate on the half-resolution minutiae grid (two source pixels).
    pub y: u8,
    /// Axial ridge-orientation bucket over 180 degrees.
    pub orientation: u8,
    /// Coarse local contrast bucket.
    pub contrast: u8,
    /// Directional coherence in `0..=100`.
    pub coherence: u8,
    /// Minutia kind: `1` ridge ending or `3` bifurcation.
    pub kind: u8,
}

#[derive(Debug, Clone, Copy)]
struct BlockFeature {
    feature: TemplateFeature,
    energy: f32,
}

#[derive(Debug, Clone, Copy)]
struct TokenCandidate {
    token: u64,
    weight: u32,
}

/// Reusable large image buffers retained once per calling thread.
///
/// Extraction is synchronous and non-reentrant, so thread-local ownership avoids
/// public lifecycle objects and locks while removing repeated allocation of the
/// oriented image, integral image, binary masks, and thinning scratch space.
#[derive(Default)]
struct ExtractionWorkspace {
    oriented: Vec<u8>,
    minutiae: MinutiaeWorkspace,
}

#[derive(Default)]
struct MinutiaeWorkspace {
    downsampled: Vec<u8>,
    integral: Vec<u32>,
    skeleton: Vec<u8>,
    cleanup_previous: Vec<u8>,
    thinning_marks: Vec<usize>,
}

thread_local! {
    static EXTRACTION_WORKSPACE: RefCell<ExtractionWorkspace> =
        RefCell::new(ExtractionWorkspace::default());
}

/// Extract a template from one raw 400x500 grayscale capture.
pub fn extract_raw_bytes(
    record_id: impl Into<String>,
    user_id: impl Into<String>,
    bytes: &[u8],
    config: ExtractorConfig,
) -> SdkResult<ExtractedTemplate> {
    let limits = SdkLimits::default();
    let config = config.validate(limits)?;
    let record_id = record_id.into();
    let user_id = user_id.into();
    validate_identifier("record_id", &record_id)?;
    validate_identifier("user_id", &user_id)?;
    if bytes.len() != RAW_LEN {
        return Err(SdkError::invalid_input(format!(
            "unexpected raw fingerprint size: got {} bytes, expected {RAW_LEN}",
            bytes.len()
        )));
    }
    EXTRACTION_WORKSPACE.with(|workspace| {
        extract_raw_fingerprint(
            bytes,
            record_id,
            user_id,
            config,
            limits,
            &mut workspace.borrow_mut(),
        )
    })
}

/// Execute the complete extraction pipeline after capture-boundary validation.
fn extract_raw_fingerprint(
    raw: &[u8],
    record_id: String,
    user_id: String,
    config: ExtractorConfig,
    limits: SdkLimits,
    workspace: &mut ExtractionWorkspace,
) -> SdkResult<ExtractedTemplate> {
    orient_vertical(raw, &mut workspace.oriented);
    let ridge_blocks = block_features(&workspace.oriented, config);
    let minutiae = minutiae_features(
        &workspace.oriented,
        &ridge_blocks,
        config,
        &mut workspace.minutiae,
    );
    let tokens = tokens_from_features(&minutiae, config);
    let token_count = u16::try_from(tokens.len())
        .map_err(|_| SdkError::resource_limit("template token count exceeds u16"))?;
    let quality = quality_from_features(
        &workspace.oriented,
        &ridge_blocks,
        config.orientation_bins,
        minutiae.len(),
    );
    let verifier_features = select_verifier_features(&minutiae, config.max_verifier_features);
    let template = ExtractedTemplate {
        record: FingerRecord { record_id, user_id },
        quality,
        token_count,
        tokens,
        features: verifier_features,
    };
    template.validate(config, limits)?;
    Ok(template)
}

/// Orient sensor rows directly into reusable matcher storage.
fn orient_vertical(raw: &[u8], oriented: &mut Vec<u8>) {
    oriented.resize(RAW_LEN, 0);
    let row = RAW_WIDTH as usize;
    for target_y in 0..RAW_HEIGHT as usize {
        let source_y = RAW_HEIGHT as usize - 1 - target_y;
        oriented[target_y * row..(target_y + 1) * row]
            .copy_from_slice(&raw[source_y * row..(source_y + 1) * row]);
    }
}

/// Estimate the ridge field and retain only blocks with usable ridge evidence.
///
/// Each `20x20` block contributes contrast, gradient energy, tensor coherence,
/// and an axial orientation. The returned map later qualifies skeleton
/// crossings, preventing background and directionally random regions from
/// generating minutiae.
fn block_features(pixels: &[u8], config: ExtractorConfig) -> Vec<BlockFeature> {
    let mut features = Vec::new();
    for by in 0..GRID_H {
        for bx in 0..GRID_W {
            let stats = block_stats(pixels, bx, by);
            if stats.stddev < config.min_block_stddev
                || stats.energy < config.min_block_energy
                || stats.coherence < config.min_block_coherence
            {
                continue;
            }
            features.push(BlockFeature {
                feature: TemplateFeature {
                    x: bx as u8,
                    y: by as u8,
                    orientation: orientation_bin(
                        stats.sum_xx,
                        stats.sum_yy,
                        stats.sum_xy,
                        config.orientation_bins,
                    ),
                    contrast: contrast_bin(stats.stddev),
                    coherence: (stats.coherence * 100.0).round().clamp(0.0, 100.0) as u8,
                    kind: 0,
                },
                energy: stats.energy,
            });
        }
    }
    features
}

/// Structure-tensor and contrast measurements for one full-resolution block.
#[derive(Debug, Clone, Copy)]
struct BlockStats {
    stddev: f32,
    energy: f32,
    coherence: f32,
    sum_xx: f32,
    sum_yy: f32,
    sum_xy: f32,
}

/// Calculate contrast and ridge-flow statistics for one `BLOCK x BLOCK` area.
///
/// Central-difference gradients accumulate `Gxx = sum(gx^2)`,
/// `Gyy = sum(gy^2)`, and `Gxy = sum(gx*gy)`. These tensor terms provide both
/// orientation and coherence without tracing individual ridges.
fn block_stats(pixels: &[u8], bx: u32, by: u32) -> BlockStats {
    let x0 = bx * BLOCK;
    let y0 = by * BLOCK;
    let mut sum = 0.0;
    let mut sum_sq = 0.0;
    let mut count = 0.0;
    let mut sum_xx = 0.0;
    let mut sum_yy = 0.0;
    let mut sum_xy = 0.0;
    for y in y0..(y0 + BLOCK) {
        for x in x0..(x0 + BLOCK) {
            let value = pixel(pixels, x, y) as f32;
            sum += value;
            sum_sq += value * value;
            count += 1.0;
            if x > 0 && x + 1 < RAW_WIDTH && y > 0 && y + 1 < RAW_HEIGHT {
                let gx = pixel(pixels, x + 1, y) as f32 - pixel(pixels, x - 1, y) as f32;
                let gy = pixel(pixels, x, y + 1) as f32 - pixel(pixels, x, y - 1) as f32;
                sum_xx += gx * gx;
                sum_yy += gy * gy;
                sum_xy += gx * gy;
            }
        }
    }
    let mean = sum / count;
    let variance = (sum_sq / count - mean * mean).max(0.0);
    let gradient_total = sum_xx + sum_yy;
    let coherence = if gradient_total <= f32::EPSILON {
        0.0
    } else {
        ((sum_xx - sum_yy).powi(2) + 4.0 * sum_xy.powi(2)).sqrt() / gradient_total
    };
    BlockStats {
        stddev: variance.sqrt(),
        energy: (gradient_total / count).sqrt(),
        coherence: coherence.clamp(0.0, 1.0),
        sum_xx,
        sum_yy,
        sum_xy,
    }
}

/// Extract ridge endings and bifurcations from a half-resolution skeleton.
///
/// The output coordinates use the `200x250` feature grid. A retained feature
/// carries the crossing kind plus the orientation and quality measurements of
/// its enclosing full-resolution ridge block.
fn minutiae_features(
    pixels: &[u8],
    ridge_blocks: &[BlockFeature],
    config: ExtractorConfig,
    workspace: &mut MinutiaeWorkspace,
) -> Vec<BlockFeature> {
    /*
    Minutiae pipeline:

    1. Average 2x2 source pixels to reduce the skeleton workload by 75%.
    2. Adaptively binarize ridges using an integral-image local mean.
    3. Remove isolated pixels/fill tiny holes, then thin ridges to one pixel.
    4. Keep only crossing numbers 1 (ending) and 3 (bifurcation).
    5. Reject border crossings and crossings outside a coherent ridge block.
    6. Suppress nearby artifacts before descriptors and verifier sampling.

    Ridge orientation comes from the block tensor instead of one skeleton arm.
    That orientation is more stable when thinning moves a branch by one pixel.
    */
    let width = FEATURE_GRID_W as usize;
    let height = FEATURE_GRID_H as usize;
    downsample_half(pixels, &mut workspace.downsampled);
    adaptive_binary(
        &workspace.downsampled,
        width,
        height,
        &mut workspace.integral,
        &mut workspace.skeleton,
    );
    cleanup_binary(
        &mut workspace.skeleton,
        width,
        height,
        &mut workspace.cleanup_previous,
    );
    thin_zhang_suen(
        &mut workspace.skeleton,
        width,
        height,
        &mut workspace.thinning_marks,
    );

    let mut block_map = vec![None; (GRID_W * GRID_H) as usize];
    for block in ridge_blocks {
        let index = (u32::from(block.feature.y) * GRID_W + u32::from(block.feature.x)) as usize;
        block_map[index] = Some(*block);
    }

    let mut candidates = Vec::new();
    for y in 8..height.saturating_sub(8) {
        for x in 8..width.saturating_sub(8) {
            if workspace.skeleton[y * width + x] == 0 {
                continue;
            }
            let crossing = crossing_number(&workspace.skeleton, width, x, y);
            if !matches!(crossing, 1 | 3) {
                continue;
            }
            let block_x = (x as u32 * 2 / BLOCK).min(GRID_W - 1);
            let block_y = (y as u32 * 2 / BLOCK).min(GRID_H - 1);
            let Some(block) = block_map[(block_y * GRID_W + block_x) as usize] else {
                continue;
            };
            candidates.push(BlockFeature {
                feature: TemplateFeature {
                    x: x as u8,
                    y: y as u8,
                    orientation: block.feature.orientation,
                    contrast: block.feature.contrast,
                    coherence: block.feature.coherence,
                    kind: crossing,
                },
                energy: block.energy,
            });
        }
    }

    candidates.sort_unstable_by(|left, right| {
        feature_weight(*right)
            .cmp(&feature_weight(*left))
            .then_with(|| left.feature.y.cmp(&right.feature.y))
            .then_with(|| left.feature.x.cmp(&right.feature.x))
    });
    let mut filtered: Vec<BlockFeature> = Vec::new();
    for candidate in candidates {
        // Five feature-grid units are ten source pixels. Keep only the strongest
        // crossing in that radius so one skeleton defect cannot dominate.
        let separated = filtered.iter().all(|existing| {
            let dx = i16::from(existing.feature.x) - i16::from(candidate.feature.x);
            let dy = i16::from(existing.feature.y) - i16::from(candidate.feature.y);
            i32::from(dx) * i32::from(dx) + i32::from(dy) * i32::from(dy) >= 25
        });
        if separated {
            filtered.push(candidate);
        }
        if filtered.len() >= config.max_verifier_features.saturating_mul(3) {
            break;
        }
    }
    filtered.sort_unstable_by_key(|feature| (feature.feature.y, feature.feature.x));
    filtered
}

/// Average each `2x2` source square into one half-resolution pixel.
///
/// Averaging provides a small low-pass filter and is more stable than selecting
/// one source pixel, while keeping the operation allocation- and branch-light.
fn downsample_half(pixels: &[u8], output: &mut Vec<u8>) {
    let width = FEATURE_GRID_W as usize;
    let height = FEATURE_GRID_H as usize;
    output.resize(width * height, 0);
    for y in 0..height {
        for x in 0..width {
            let source_x = x * 2;
            let source_y = y * 2;
            let sum = u16::from(pixels[source_y * RAW_WIDTH as usize + source_x])
                + u16::from(pixels[source_y * RAW_WIDTH as usize + source_x + 1])
                + u16::from(pixels[(source_y + 1) * RAW_WIDTH as usize + source_x])
                + u16::from(pixels[(source_y + 1) * RAW_WIDTH as usize + source_x + 1]);
            output[y * width + x] = (sum / 4) as u8;
        }
    }
}

/// Convert grayscale pixels to a ridge mask using an `11x11` local mean.
///
/// An integral image makes every window sum constant-time. Darker-than-local
/// pixels become ridge foreground; the near-white guard avoids inventing ridges
/// from tiny variations in an empty capture.
fn adaptive_binary(
    pixels: &[u8],
    width: usize,
    height: usize,
    integral: &mut Vec<u32>,
    binary: &mut Vec<u8>,
) {
    let integral_width = width + 1;
    integral.resize(integral_width * (height + 1), 0);
    integral.fill(0);
    for y in 0..height {
        let mut row_sum = 0u32;
        for x in 0..width {
            row_sum += u32::from(pixels[y * width + x]);
            integral[(y + 1) * integral_width + x + 1] =
                integral[y * integral_width + x + 1] + row_sum;
        }
    }
    let radius = 5usize;
    binary.resize(width * height, 0);
    for y in 0..height {
        let y0 = y.saturating_sub(radius);
        let y1 = (y + radius + 1).min(height);
        for x in 0..width {
            let x0 = x.saturating_sub(radius);
            let x1 = (x + radius + 1).min(width);
            let sum = integral[y1 * integral_width + x1] + integral[y0 * integral_width + x0]
                - integral[y0 * integral_width + x1]
                - integral[y1 * integral_width + x0];
            let area = (x1 - x0) * (y1 - y0);
            let mean = sum as f32 / area.max(1) as f32;
            let value = pixels[y * width + x];
            binary[y * width + x] = u8::from(value < 245 && f32::from(value) + 6.0 < mean);
        }
    }
}

/// Apply two conservative morphology passes before thinning.
///
/// Isolated ridge tips with at most one neighbor are removed and one-pixel holes
/// surrounded by at least seven ridge pixels are filled. More aggressive
/// morphology could join separate ridges and create false bifurcations.
fn cleanup_binary(binary: &mut [u8], width: usize, height: usize, previous: &mut Vec<u8>) {
    previous.resize(binary.len(), 0);
    for _ in 0..2 {
        previous.copy_from_slice(binary);
        for y in 1..height - 1 {
            for x in 1..width - 1 {
                let neighbors = neighbors(previous, width, x, y)
                    .into_iter()
                    .filter(|value| *value)
                    .count();
                let index = y * width + x;
                if previous[index] != 0 && neighbors <= 1 {
                    binary[index] = 0;
                } else if previous[index] == 0 && neighbors >= 7 {
                    binary[index] = 1;
                }
            }
        }
    }
}

/// Reduce binary ridges to a one-pixel skeleton with Zhang-Suen thinning.
///
/// The two directional subpasses remove boundary pixels only when neighbor and
/// transition conditions preserve local connectivity. Convergence normally
/// stops the loop; 32 iterations provide a hard runtime bound for bad input.
fn thin_zhang_suen(binary: &mut [u8], width: usize, height: usize, marked: &mut Vec<usize>) {
    for _ in 0..32 {
        let mut changed = false;
        for second_pass in [false, true] {
            marked.clear();
            for y in 1..height - 1 {
                for x in 1..width - 1 {
                    let index = y * width + x;
                    if binary[index] == 0 {
                        continue;
                    }
                    let points = neighbors(binary, width, x, y);
                    let count = points.iter().filter(|value| **value).count();
                    if !(2..=6).contains(&count) || transitions(&points) != 1 {
                        continue;
                    }
                    let [p2, _, p4, _, p6, _, p8, _] = points;
                    let removable = if second_pass {
                        !p2 || !p8 || (!p4 && !p6)
                    } else {
                        !p4 || !p6 || (!p2 && !p8)
                    };
                    if removable {
                        marked.push(index);
                    }
                }
            }
            if !marked.is_empty() {
                changed = true;
                for index in marked.iter() {
                    binary[*index] = 0;
                }
            }
        }
        if !changed {
            break;
        }
    }
}

/// Return `P2..P9` clockwise around `(x, y)`, starting at north.
fn neighbors(binary: &[u8], width: usize, x: usize, y: usize) -> [bool; 8] {
    [
        binary[(y - 1) * width + x] != 0,
        binary[(y - 1) * width + x + 1] != 0,
        binary[y * width + x + 1] != 0,
        binary[(y + 1) * width + x + 1] != 0,
        binary[(y + 1) * width + x] != 0,
        binary[(y + 1) * width + x - 1] != 0,
        binary[y * width + x - 1] != 0,
        binary[(y - 1) * width + x - 1] != 0,
    ]
}

/// Count circular background-to-ridge transitions around eight neighbors.
///
/// On a one-pixel skeleton this count is the crossing number: one transition is
/// a ridge ending and three transitions are a bifurcation.
fn transitions(points: &[bool; 8]) -> u8 {
    let mut transitions = 0;
    for index in 0..points.len() {
        if !points[index] && points[(index + 1) % points.len()] {
            transitions += 1;
        }
    }
    transitions
}

/// Return the crossing number of one foreground skeleton pixel.
fn crossing_number(binary: &[u8], width: usize, x: usize, y: usize) -> u8 {
    transitions(&neighbors(binary, width, x, y))
}

fn pixel(pixels: &[u8], x: u32, y: u32) -> u8 {
    pixels[(y * RAW_WIDTH + x) as usize]
}

/// Quantize structure-tensor ridge orientation over the axial 180-degree range.
///
/// The `0.5 * atan2(2*Gxy, Gxx-Gyy)` form makes opposite ridge directions
/// equivalent, as required for an unoriented ridge line.
fn orientation_bin(sum_xx: f32, sum_yy: f32, sum_xy: f32, bins: u8) -> u8 {
    let angle = 0.5 * (2.0 * sum_xy).atan2(sum_xx - sum_yy);
    let normalized = (angle + std::f32::consts::FRAC_PI_2) / std::f32::consts::PI;
    ((normalized.rem_euclid(1.0) * f32::from(bins)) as u8).min(bins - 1)
}

/// Quantize local standard deviation into the persisted three-bit range.
fn contrast_bin(stddev: f32) -> u8 {
    (stddev / 8.0).floor().clamp(0.0, 7.0) as u8
}

/// Build invariant pair/triplet tokens from local minutiae relationships.
///
/// Each minutia anchors descriptors to its nearest neighbors. Distances and
/// angles are relative, so translation is removed and global rotation mostly
/// cancels against the anchor's ridge orientation. Pair descriptors favor
/// recall; the additional triplet descriptor is more discriminative.
fn tokens_from_features(features: &[BlockFeature], config: ExtractorConfig) -> Vec<u64> {
    let mut candidates = Vec::new();
    for (anchor_index, anchor) in features.iter().enumerate() {
        let mut neighbors = features
            .iter()
            .enumerate()
            .filter_map(|(index, feature)| {
                if index == anchor_index {
                    return None;
                }
                let dx = i16::from(feature.feature.x) - i16::from(anchor.feature.x);
                let dy = i16::from(feature.feature.y) - i16::from(anchor.feature.y);
                let distance_squared =
                    i32::from(dx) * i32::from(dx) + i32::from(dy) * i32::from(dy);
                (distance_squared > 0 && distance_squared <= 6_400).then_some((
                    distance_squared,
                    index,
                    dx,
                    dy,
                ))
            })
            .collect::<Vec<_>>();
        neighbors.sort_unstable_by_key(|(distance, index, _, _)| (*distance, *index));
        neighbors.truncate(config.max_neighbors);

        for (distance_squared, neighbor_index, dx, dy) in &neighbors {
            let neighbor = features[*neighbor_index];
            let descriptor = pair_descriptor(
                anchor.feature,
                neighbor.feature,
                *distance_squared,
                *dx,
                *dy,
                config.orientation_bins,
            );
            candidates.push(TokenCandidate {
                token: token_hash(2, &descriptor),
                weight: feature_weight(*anchor).saturating_add(feature_weight(neighbor)) / 2,
            });
        }

        if neighbors.len() >= 2 {
            let first = neighbors[0];
            let second = neighbors[1];
            let first_feature = features[first.1].feature;
            let second_feature = features[second.1].feature;
            let first_descriptor = pair_descriptor(
                anchor.feature,
                first_feature,
                first.0,
                first.2,
                first.3,
                config.orientation_bins,
            );
            let second_descriptor = pair_descriptor(
                anchor.feature,
                second_feature,
                second.0,
                second.2,
                second.3,
                config.orientation_bins,
            );
            let mut descriptor = [0; 11];
            descriptor[..7].copy_from_slice(&first_descriptor);
            descriptor[7..].copy_from_slice(&second_descriptor[..4]);
            candidates.push(TokenCandidate {
                token: token_hash(3, &descriptor),
                weight: feature_weight(*anchor)
                    .saturating_add(feature_weight(features[first.1]))
                    .saturating_add(feature_weight(features[second.1]))
                    / 3,
            });
        }
    }
    select_tokens(candidates, config.max_tokens)
}

/// Quantize one anchor-to-neighbor relationship into seven descriptor fields.
///
/// The fields are distance, anchor-relative bearing, relative ridge
/// orientation, both minutia kinds, minimum contrast, and minimum coherence.
/// Quantization absorbs small extraction changes before deterministic hashing.
fn pair_descriptor(
    anchor: TemplateFeature,
    neighbor: TemplateFeature,
    distance_squared: i32,
    dx: i16,
    dy: i16,
    orientation_bins: u8,
) -> [u8; 7] {
    let distance_bucket = ((distance_squared as f32).sqrt() / 4.0)
        .round()
        .clamp(0.0, 31.0) as u8;
    let spatial_angle = (f32::from(dy)).atan2(f32::from(dx));
    let spatial_bin = (((spatial_angle + std::f32::consts::PI) / (2.0 * std::f32::consts::PI)
        * 32.0)
        .floor() as i16)
        .rem_euclid(32);
    let anchor_full_circle = i16::from(anchor.orientation) * 32 / i16::from(orientation_bins);
    let relative_bearing = ((spatial_bin - anchor_full_circle).rem_euclid(32) / 2) as u8;
    let orientation_delta = (i16::from(neighbor.orientation) - i16::from(anchor.orientation))
        .rem_euclid(i16::from(orientation_bins)) as u8
        / 2;
    [
        distance_bucket,
        relative_bearing,
        orientation_delta,
        anchor.kind,
        neighbor.kind,
        anchor.contrast.min(neighbor.contrast) / 2,
        anchor.coherence.min(neighbor.coherence) / 25,
    ]
}

/// Rank a minutia by local ridge evidence for bounded feature selection.
fn feature_weight(feature: BlockFeature) -> u32 {
    feature.energy.max(0.0) as u32
        + u32::from(feature.feature.contrast) * 8
        + u32::from(feature.feature.coherence)
        + u32::from(feature.feature.kind == 3) * 12
}

/// Deduplicate descriptor hashes and retain the strongest bounded set.
///
/// Final numeric sorting is required by template validation and index lookup; it
/// does not represent descriptor importance.
fn select_tokens(candidates: Vec<TokenCandidate>, max_tokens: usize) -> Vec<u64> {
    let mut best: HashMap<u64, u32> = HashMap::new();
    for candidate in candidates {
        best.entry(candidate.token)
            .and_modify(|weight| *weight = (*weight).max(candidate.weight))
            .or_insert(candidate.weight);
    }
    let mut candidates = best
        .into_iter()
        .map(|(token, weight)| TokenCandidate { token, weight })
        .collect::<Vec<_>>();
    candidates.sort_unstable_by(|left, right| {
        right
            .weight
            .cmp(&left.weight)
            .then_with(|| left.token.cmp(&right.token))
    });
    candidates.truncate(max_tokens);
    let mut tokens = candidates
        .into_iter()
        .map(|candidate| candidate.token)
        .collect::<Vec<_>>();
    tokens.sort_unstable();
    tokens
}

/// Select a compact, spatially distributed minutiae set for verification.
///
/// The strongest minutia in each `12x12` half-resolution cell competes for the
/// final bounded set. This keeps one noisy local cluster from consuming all 64
/// default verifier slots.
fn select_verifier_features(features: &[BlockFeature], limit: usize) -> Vec<TemplateFeature> {
    let mut cells: HashMap<(u8, u8), BlockFeature> = HashMap::new();
    for feature in features {
        let cell = (feature.feature.x / 12, feature.feature.y / 12);
        cells
            .entry(cell)
            .and_modify(|current| {
                if feature_weight(*feature) > feature_weight(*current) {
                    *current = *feature;
                }
            })
            .or_insert(*feature);
    }
    let mut selected = cells.into_values().collect::<Vec<_>>();
    selected.sort_unstable_by(|left, right| {
        feature_weight(*right)
            .cmp(&feature_weight(*left))
            .then_with(|| left.feature.y.cmp(&right.feature.y))
            .then_with(|| left.feature.x.cmp(&right.feature.x))
    });
    selected.truncate(limit);
    selected.sort_unstable_by_key(|feature| (feature.feature.y, feature.feature.x));
    selected
        .into_iter()
        .map(|feature| feature.feature)
        .collect()
}

/// Hash a quantized descriptor with a pair/triplet domain byte.
///
/// FNV-1a is used for stable compact indexing, not security. Token equality only
/// retrieves candidates; geometric verification handles accidental collisions.
fn token_hash(kind: u8, values: &[u8]) -> u64 {
    let mut hash = 0xcbf29ce484222325u64;
    hash ^= u64::from(kind);
    hash = hash.wrapping_mul(0x100000001b3);
    for value in values {
        hash ^= u64::from(*value);
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

/// Score capture usability from ridge-field, image, and minutiae evidence.
///
/// This is an SDK-specific `0..=100` scale, not NFIQ2. It rewards usable ridge
/// coverage, coherent flow, orientation diversity, contrast, and a plausible
/// minutiae count while penalizing empty, saturated, noisy, or striped images.
fn quality_from_features(
    pixels: &[u8],
    features: &[BlockFeature],
    orientation_bins: u8,
    minutiae_count: usize,
) -> u8 {
    if features.len() < 8 {
        return 0;
    }
    let grid_blocks = (GRID_W * GRID_H) as f32;
    let coverage = features.len() as f32 / grid_blocks;
    let mean_coherence = features
        .iter()
        .map(|feature| f32::from(feature.feature.coherence) / 100.0)
        .sum::<f32>()
        / features.len() as f32;
    let mean_contrast = features
        .iter()
        .map(|feature| f32::from(feature.feature.contrast) / 7.0)
        .sum::<f32>()
        / features.len() as f32;

    let mut orientation_x = 0.0;
    let mut orientation_y = 0.0;
    for feature in features {
        let angle = f32::from(feature.feature.orientation) / f32::from(orientation_bins)
            * std::f32::consts::PI;
        orientation_x += (2.0 * angle).cos();
        orientation_y += (2.0 * angle).sin();
    }
    let orientation_concentration =
        orientation_x.hypot(orientation_y) / features.len().max(1) as f32;
    let orientation_diversity = ((1.0 - orientation_concentration) / 0.65).clamp(0.0, 1.0);

    let active_pixels = pixels.iter().filter(|pixel| **pixel < 245).count();
    let dark_saturated = pixels.iter().filter(|pixel| **pixel <= 5).count();
    let foreground = active_pixels as f32 / pixels.len() as f32;
    let dark_saturation = dark_saturated as f32 / active_pixels.max(1) as f32;

    let coverage_score = if coverage < 0.08 {
        coverage / 0.08
    } else if coverage <= 0.78 {
        1.0
    } else {
        (1.0 - (coverage - 0.78) / 0.22).clamp(0.0, 1.0)
    };
    let foreground_score = if foreground < 0.06 {
        foreground / 0.06
    } else if foreground <= 0.82 {
        1.0
    } else {
        (1.0 - (foreground - 0.82) / 0.18).clamp(0.0, 1.0)
    };
    let coherence_score = ((mean_coherence - 0.18) / 0.62).clamp(0.0, 1.0);
    let saturation_score = (1.0 - dark_saturation / 0.55).clamp(0.0, 1.0);

    let mut score = 100.0
        * (coverage_score * 0.20
            + foreground_score * 0.15
            + coherence_score * 0.30
            + orientation_diversity * 0.20
            + mean_contrast * 0.10
            + saturation_score * 0.05);

    if orientation_concentration > 0.92 {
        score *= 0.25;
    }
    if foreground > 0.92 && mean_coherence < 0.35 {
        score *= 0.15;
    }
    if features.len() < 30 {
        score *= features.len() as f32 / 30.0;
    }
    let minutiae_score = if minutiae_count < 12 {
        minutiae_count as f32 / 12.0
    } else if minutiae_count <= 100 {
        1.0
    } else {
        (1.0 - (minutiae_count - 100) as f32 / 200.0).clamp(0.35, 1.0)
    };
    score = score * 0.75 + minutiae_score * 25.0;
    if minutiae_count < 6 {
        score = score.min(30.0);
    }
    score.round().clamp(0.0, 100.0) as u8
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::fingerprint::RAW_LEN;

    #[test]
    fn rejects_invalid_orientation_configuration_without_panicking() {
        let config = ExtractorConfig {
            orientation_bins: 0,
            ..ExtractorConfig::default()
        };
        let error = extract_raw_bytes("r", "u", &vec![0; RAW_LEN], config)
            .expect_err("zero orientation bins must fail");
        assert_eq!(
            error.code(),
            super::super::error::SdkErrorCode::InvalidInput
        );
    }

    #[test]
    fn noise_and_parallel_stripes_are_not_high_quality_fingerprints() {
        let noise = lcg_noise();
        let stripes = parallel_stripes();
        let noise_template =
            extract_raw_bytes("noise", "u", &noise, ExtractorConfig::default()).unwrap();
        let stripe_template =
            extract_raw_bytes("stripes", "u", &stripes, ExtractorConfig::default()).unwrap();
        assert!(noise_template.quality < 45, "{}", noise_template.quality);
        assert!(stripe_template.quality < 45, "{}", stripe_template.quality);
    }

    #[test]
    fn reused_workspace_does_not_leak_previous_capture_state() {
        let stripes = parallel_stripes();
        let first =
            extract_raw_bytes("same", "user", &stripes, ExtractorConfig::default()).unwrap();
        let _ =
            extract_raw_bytes("noise", "other", &lcg_noise(), ExtractorConfig::default()).unwrap();
        let second =
            extract_raw_bytes("same", "user", &stripes, ExtractorConfig::default()).unwrap();
        assert_eq!(first, second);
    }

    fn parallel_stripes() -> Vec<u8> {
        (0..RAW_LEN)
            .map(|index| {
                if (index % RAW_WIDTH as usize) % 8 < 4 {
                    0
                } else {
                    255
                }
            })
            .collect()
    }

    fn lcg_noise() -> Vec<u8> {
        let mut state = 0x1234_5678u32;
        (0..RAW_LEN)
            .map(|_| {
                state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
                (state >> 24) as u8
            })
            .collect()
    }
}
