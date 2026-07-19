//! In-memory candidate index and geometric template verifier.
//!
//! Search is intentionally two-stage. Descriptor tokens nominate a bounded
//! shortlist through an inverted index; [`verify_template_geometry`] then checks
//! bounded rotation/translation hypotheses, one-to-one minutiae pairings, and
//! their supporting-edge consistency. The final record score blends retrieval
//! and verification evidence before
//! [`BiometricIndex::search_users`] collapses finger records to application
//! users.
//!
//! See `docs/matcher-algorithm.md` for equations, coordinate units, default
//! bounds, pseudocode, and limitations.

use std::cell::RefCell;
use std::collections::{BTreeMap, HashMap, HashSet};
use std::io::{Read, Write};

use serde::{Deserialize, Serialize};

use super::error::{SdkError, SdkResult};
use super::extractor::{
    ExtractedTemplate, ExtractorConfig, FEATURE_GRID_H, FEATURE_GRID_W, TemplateFeature,
};
use super::limits::SdkLimits;
use super::persist;
use super::storage::validate_identifier;

/// Search-time controls for candidate generation and result shaping.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SearchConfig {
    /// Maximum final user hits returned.
    pub top_k: usize,
    /// Second-stage verifier settings.
    pub rerank: RerankConfig,
}

impl Default for SearchConfig {
    fn default() -> Self {
        Self {
            top_k: 20,
            rerank: RerankConfig::default(),
        }
    }
}

impl SearchConfig {
    fn normalized(mut self) -> Self {
        self.top_k = self.top_k.clamp(1, 100);
        self.rerank = self.rerank.normalized();
        self
    }
}

/// Controls for geometric candidate verification.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct RerankConfig {
    /// Enable geometric verification.
    pub enabled: bool,
    /// Candidate records entering the verifier.
    pub candidate_limit: usize,
    /// Maximum translation searched in half-resolution minutiae-grid units.
    pub max_translation_units: i16,
    /// Maximum global rotation searched in orientation bins.
    pub max_rotation_bins: u8,
    /// Coordinate tolerance in half-resolution minutiae-grid units.
    pub position_tolerance_units: u8,
    /// Orientation tolerance after applying a transform hypothesis.
    pub orientation_tolerance_bins: u8,
    /// Weight of geometric verification in the final score.
    pub verification_weight: f32,
}

impl Default for RerankConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            candidate_limit: 8,
            max_translation_units: 32,
            max_rotation_bins: 2,
            position_tolerance_units: 3,
            orientation_tolerance_bins: 1,
            verification_weight: 0.68,
        }
    }
}

impl RerankConfig {
    fn normalized(mut self) -> Self {
        self.candidate_limit = self.candidate_limit.clamp(1, 256);
        self.max_translation_units = self.max_translation_units.clamp(0, 64);
        self.max_rotation_bins = self.max_rotation_bins.min(8);
        self.position_tolerance_units = self.position_tolerance_units.min(4);
        self.orientation_tolerance_bins = self.orientation_tolerance_bins.min(8);
        self.verification_weight = if self.verification_weight.is_finite() {
            self.verification_weight.clamp(0.0, 1.0)
        } else {
            RerankConfig::default().verification_weight
        };
        self
    }
}

/// Clock-in identification policy owned by the SDK.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct IdentifyConfig {
    /// Candidate search settings.
    pub search: SearchConfig,
    /// Minimum query quality before searching.
    pub min_quality: u8,
    /// Minimum blended score accepted as a match.
    pub min_score: f32,
    /// Minimum geometric score accepted as a match.
    pub min_verification_score: f32,
    /// Minimum gap between the first and second user.
    pub min_margin: f32,
}

impl Default for IdentifyConfig {
    fn default() -> Self {
        let search = SearchConfig {
            top_k: 2,
            ..SearchConfig::default()
        };
        Self {
            search,
            min_quality: 40,
            min_score: 0.30,
            min_verification_score: 0.20,
            min_margin: 0.02,
        }
    }
}

impl IdentifyConfig {
    pub(crate) fn normalized(mut self) -> Self {
        self.search = self.search.normalized();
        self.search.top_k = self.search.top_k.max(2);
        self.min_quality = self.min_quality.min(100);
        self.min_score = finite_unit(self.min_score, IdentifyConfig::default().min_score);
        self.min_verification_score = finite_unit(
            self.min_verification_score,
            IdentifyConfig::default().min_verification_score,
        );
        self.min_margin = finite_unit(self.min_margin, IdentifyConfig::default().min_margin);
        self
    }
}

/// Successful user identification.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct IdentifyMatch {
    /// Matched application user identifier.
    pub user_id: String,
    /// Finger record producing the best match.
    pub record_id: String,
    /// Final blended score.
    pub score: f32,
    /// Geometric verification score.
    pub verification_score: f32,
    /// Shared candidate descriptors.
    pub votes: u16,
}

/// Reason a scan should be retried.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum IdentifyRetryReason {
    /// Query quality was below policy.
    LowQuality,
    /// No indexed record shared a candidate descriptor.
    NoCandidates,
    /// Best candidate was below the acceptance floor.
    WeakScore,
    /// Best and runner-up users were too close.
    Ambiguous,
}

/// Retry response containing optional diagnostics.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct IdentifyRetry {
    /// Retry reason.
    pub reason: IdentifyRetryReason,
    /// Best observed candidate, when available.
    pub best_hit: Option<SearchHit>,
}

/// Identification result returned to clock-in integrations.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum IdentifyResult {
    /// Accepted user match.
    Match(IdentifyMatch),
    /// Scan needs another attempt.
    Retry(IdentifyRetry),
}

/// Final user-level search hit.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct SearchHit {
    /// User identifier.
    pub user_id: String,
    /// Best finger record for the user.
    pub record_id: String,
    /// Shared descriptor count.
    pub votes: u16,
    /// Final score.
    pub score: f32,
    /// Geometric score.
    pub verification_score: f32,
}

/// Record-level hit before user collapsing.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RecordSearchHit {
    /// Record owner.
    pub user_id: String,
    /// Finger record identifier.
    pub record_id: String,
    /// Shared descriptor count.
    pub votes: u16,
    /// Candidate-stage score.
    pub overlap_score: f32,
    /// Geometric score.
    pub verification_score: f32,
    /// Final blended score.
    pub score: f32,
}

/// Validated in-memory biometric index.
///
/// `dictionary` and `postings` implement `token -> finger records` retrieval;
/// `record_features` stores the small minutiae sets needed only after a record
/// reaches the shortlist. Keeping both representations avoids a geometric scan
/// of every enrolled finger.
#[derive(Debug, Clone, PartialEq)]
pub struct BiometricIndex {
    pub(crate) orientation_bins: u8,
    pub(crate) records: Vec<IndexRecord>,
    pub(crate) record_features: Vec<RecordFeatures>,
    pub(crate) dictionary: Vec<TokenPosting>,
    pub(crate) postings: Vec<u32>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct IndexRecord {
    pub(crate) record_id: String,
    pub(crate) user_id: String,
    pub(crate) token_count: u16,
    pub(crate) quality: u8,
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct TokenPosting {
    /// Invariant pair/triplet descriptor hash.
    pub(crate) token: u64,
    /// First element in the shared flat postings vector.
    pub(crate) start: u32,
    /// Number of record indexes in this token's posting list.
    pub(crate) len: u32,
    /// Distinct owners represented in the list, used for IDF weighting.
    pub(crate) user_count: u32,
    /// Precomputed inverse-user-frequency contribution.
    pub(crate) idf: f32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct RecordFeatures {
    pub(crate) features: Vec<TemplateFeature>,
    /// Prefix offsets into `orientation_members`, one per orientation plus end.
    orientation_offsets: Vec<u16>,
    /// Feature indexes grouped by orientation for verifier candidate pruning.
    orientation_members: Vec<u16>,
    /// Occupied coarse spatial cells in key order.
    spatial_cells: Vec<SpatialCell>,
    /// Feature indexes grouped by coarse spatial cell.
    spatial_members: Vec<u16>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct SpatialCell {
    key: u16,
    start: u16,
    len: u16,
}

#[derive(Debug, Clone)]
struct CandidateHit {
    record_index: usize,
    votes: u16,
    overlap_score: f32,
    verification_score: f32,
    score: f32,
}

#[derive(Debug, Clone, Copy, Default)]
struct CandidateAccumulator {
    votes: u16,
    weighted_votes: f32,
}

#[derive(Debug, Clone, Copy)]
struct RotatedFeature {
    x: i16,
    y: i16,
    orientation: u8,
}

#[derive(Debug)]
struct PreparedQuery {
    rotations: Vec<i8>,
    rotated: Vec<RotatedFeature>,
    assignment_order: Vec<u16>,
}

#[derive(Debug, Clone, Copy)]
struct TransformHypothesis {
    rotation_index: usize,
    dx: i16,
    dy: i16,
    support: u16,
}

#[derive(Debug, Clone, Copy)]
struct MinutiaPairing {
    query: u16,
    candidate: u16,
}

/// Reusable search scratch retained once per calling thread.
#[derive(Default)]
struct SearchWorkspace {
    accumulators: Vec<CandidateAccumulator>,
    votes: Vec<u16>,
    used: Vec<bool>,
    pairings: Vec<MinutiaPairing>,
}

thread_local! {
    static SEARCH_WORKSPACE: RefCell<SearchWorkspace> =
        RefCell::new(SearchWorkspace::default());
}

const MAX_SUPPORTING_EDGE_CHECKS: usize = 256;
const EARLY_VERIFICATION_SCORE: f32 = 0.72;
const SPATIAL_CELL_SIZE: i16 = 8;
const SPATIAL_CELL_COLUMNS: i16 = FEATURE_GRID_W as i16 / SPATIAL_CELL_SIZE;

impl RecordFeatures {
    pub(crate) fn new(features: Vec<TemplateFeature>, orientation_bins: u8) -> Self {
        let bin_count = usize::from(orientation_bins.max(1));
        let mut counts = vec![0u16; bin_count];
        for feature in &features {
            if let Some(count) = counts.get_mut(usize::from(feature.orientation)) {
                *count = count.saturating_add(1);
            }
        }
        let mut orientation_offsets = Vec::with_capacity(counts.len() + 1);
        orientation_offsets.push(0);
        for count in counts {
            let next = orientation_offsets
                .last()
                .copied()
                .unwrap_or(0u16)
                .saturating_add(count);
            orientation_offsets.push(next);
        }
        let mut cursors = orientation_offsets[..orientation_offsets.len() - 1].to_vec();
        let member_count = usize::from(*orientation_offsets.last().unwrap_or(&0));
        let mut orientation_members = vec![0; member_count];
        for (index, feature) in features.iter().enumerate() {
            let orientation = usize::from(feature.orientation);
            if orientation >= bin_count {
                continue;
            }
            let destination = usize::from(cursors[orientation]);
            orientation_members[destination] = index as u16;
            cursors[orientation] = cursors[orientation].saturating_add(1);
        }
        let mut keyed_features = features
            .iter()
            .enumerate()
            .map(|(index, feature)| {
                let cell_x = i16::from(feature.x) / SPATIAL_CELL_SIZE;
                let cell_y = i16::from(feature.y) / SPATIAL_CELL_SIZE;
                (
                    (cell_y * SPATIAL_CELL_COLUMNS + cell_x) as u16,
                    index as u16,
                )
            })
            .collect::<Vec<_>>();
        keyed_features.sort_unstable_by_key(|(key, index)| (*key, *index));
        let mut spatial_cells = Vec::new();
        let mut spatial_members = Vec::with_capacity(keyed_features.len());
        for (key, index) in keyed_features {
            if spatial_cells
                .last()
                .is_none_or(|cell: &SpatialCell| cell.key != key)
            {
                spatial_cells.push(SpatialCell {
                    key,
                    start: spatial_members.len() as u16,
                    len: 0,
                });
            }
            spatial_members.push(index);
            if let Some(cell) = spatial_cells.last_mut() {
                cell.len = cell.len.saturating_add(1);
            }
        }
        Self {
            features,
            orientation_offsets,
            orientation_members,
            spatial_cells,
            spatial_members,
        }
    }

    fn orientation_bucket(&self, orientation: u8) -> &[u16] {
        let orientation = usize::from(orientation);
        let start = usize::from(self.orientation_offsets[orientation]);
        let end = usize::from(self.orientation_offsets[orientation + 1]);
        &self.orientation_members[start..end]
    }

    fn spatial_bucket(&self, cell_x: i16, cell_y: i16) -> &[u16] {
        if cell_x < 0
            || cell_y < 0
            || cell_x >= SPATIAL_CELL_COLUMNS
            || cell_y >= (FEATURE_GRID_H as i16 + SPATIAL_CELL_SIZE - 1) / SPATIAL_CELL_SIZE
        {
            return &[];
        }
        let key = (cell_y * SPATIAL_CELL_COLUMNS + cell_x) as u16;
        let Ok(index) = self
            .spatial_cells
            .binary_search_by_key(&key, |cell| cell.key)
        else {
            return &[];
        };
        let cell = self.spatial_cells[index];
        let start = usize::from(cell.start);
        let end = start + usize::from(cell.len);
        &self.spatial_members[start..end]
    }
}

impl BiometricIndex {
    /// Build an index using the default extractor profile.
    pub fn build(templates: &[ExtractedTemplate]) -> SdkResult<Self> {
        Self::build_with_config(templates, ExtractorConfig::default(), SdkLimits::default())
    }

    /// Build an index for the exact extractor profile stored in a bundle.
    ///
    /// Templates are already strictly sorted and deduplicated. Building merges
    /// their token streams into a sorted dictionary and contiguous posting
    /// lists, while retaining each record's bounded verifier minutiae.
    pub fn build_with_config(
        templates: &[ExtractedTemplate],
        config: ExtractorConfig,
        limits: SdkLimits,
    ) -> SdkResult<Self> {
        let config = config.validate(limits)?;
        if templates.len() > limits.max_records {
            return Err(SdkError::resource_limit("location record limit exceeded"));
        }
        for template in templates {
            template.validate(config, limits)?;
        }

        let records = templates
            .iter()
            .map(|template| IndexRecord {
                record_id: template.record.record_id.clone(),
                user_id: template.record.user_id.clone(),
                token_count: template.token_count,
                quality: template.quality,
            })
            .collect::<Vec<_>>();
        let record_features = templates
            .iter()
            .map(|template| RecordFeatures::new(template.features.clone(), config.orientation_bins))
            .collect::<Vec<_>>();

        // A BTreeMap emits the persisted dictionary in token order, allowing
        // query-time binary search without another sorting pass.
        let mut token_map: BTreeMap<u64, Vec<u32>> = BTreeMap::new();
        for (record_index, template) in templates.iter().enumerate() {
            let record_index = u32::try_from(record_index)
                .map_err(|_| SdkError::resource_limit("record index exceeds u32"))?;
            for token in &template.tokens {
                token_map.entry(*token).or_default().push(record_index);
            }
        }

        let mut dictionary = Vec::with_capacity(token_map.len());
        let mut postings = Vec::new();
        let total_users = records
            .iter()
            .map(|record| record.user_id.as_str())
            .collect::<HashSet<_>>()
            .len()
            .max(1) as f32;
        for (token, mut token_postings) in token_map {
            token_postings.sort_unstable();
            token_postings.dedup();
            let users = token_postings
                .iter()
                .map(|record_index| records[*record_index as usize].user_id.as_str())
                .collect::<HashSet<_>>()
                .len();
            let start = u32::try_from(postings.len())
                .map_err(|_| SdkError::resource_limit("index postings exceed u32"))?;
            let len = u32::try_from(token_postings.len())
                .map_err(|_| SdkError::resource_limit("posting list exceeds u32"))?;
            let user_count = u32::try_from(users)
                .map_err(|_| SdkError::resource_limit("posting user count exceeds u32"))?;
            let idf = ((total_users + 1.0) / (user_count as f32 + 0.5)).ln();
            postings.extend(token_postings);
            dictionary.push(TokenPosting {
                token,
                start,
                len,
                user_count,
                idf,
            });
        }

        let index = Self {
            orientation_bins: config.orientation_bins,
            records,
            record_features,
            dictionary,
            postings,
        };
        index.validate(limits)?;
        Ok(index)
    }

    /// Encode this index to bytes.
    pub fn to_bytes(&self) -> SdkResult<Vec<u8>> {
        persist::encode_index(self)
    }

    /// Decode and structurally validate an index.
    pub fn from_bytes(bytes: &[u8]) -> SdkResult<Self> {
        persist::decode_index(bytes, SdkLimits::default())
    }

    /// Write this index to a stream.
    pub fn write_to(&self, writer: impl Write) -> SdkResult<()> {
        persist::write_index(self, writer)
    }

    /// Read and structurally validate an index from a stream.
    pub fn read_from(reader: impl Read) -> SdkResult<Self> {
        persist::read_index(reader, SdkLimits::default())
    }

    /// Search and collapse verified record hits to users.
    ///
    /// Both enrolled thumbs remain independent record candidates. Only the
    /// strongest record for each owner survives, so the caller receives user
    /// identities rather than finger labels.
    pub fn search_users(
        &self,
        query: &ExtractedTemplate,
        config: SearchConfig,
    ) -> SdkResult<Vec<SearchHit>> {
        let config = config.normalized();
        let mut best_by_user: HashMap<String, SearchHit> = HashMap::new();
        for hit in self.search_records(query, config)? {
            let user_hit = SearchHit {
                user_id: hit.user_id,
                record_id: hit.record_id,
                votes: hit.votes,
                score: hit.score,
                verification_score: hit.verification_score,
            };
            best_by_user
                .entry(user_hit.user_id.clone())
                .and_modify(|current| {
                    if compare_hit_values(
                        user_hit.score,
                        user_hit.votes,
                        &user_hit.record_id,
                        current.score,
                        current.votes,
                        &current.record_id,
                    )
                    .is_lt()
                    {
                        *current = user_hit.clone();
                    }
                })
                .or_insert(user_hit);
        }
        let mut hits = best_by_user.into_values().collect::<Vec<_>>();
        hits.sort_by(|left, right| {
            compare_hit_values(
                left.score,
                left.votes,
                &left.user_id,
                right.score,
                right.votes,
                &right.user_id,
            )
        });
        hits.truncate(config.top_k);
        Ok(hits)
    }

    /// Identify a user or request another scan.
    ///
    /// Ranking first is necessary but insufficient. Acceptance also requires
    /// query quality, blended score, geometric score, and separation from the
    /// runner-up user to pass the bundle's policy.
    pub fn identify_user(
        &self,
        query: &ExtractedTemplate,
        config: IdentifyConfig,
    ) -> SdkResult<IdentifyResult> {
        let config = config.normalized();
        if query.quality < config.min_quality {
            return Ok(IdentifyResult::Retry(IdentifyRetry {
                reason: IdentifyRetryReason::LowQuality,
                best_hit: None,
            }));
        }
        let hits = self.search_users(query, config.search)?;
        let Some(best) = hits.first().cloned() else {
            return Ok(IdentifyResult::Retry(IdentifyRetry {
                reason: IdentifyRetryReason::NoCandidates,
                best_hit: None,
            }));
        };
        if best.score < config.min_score || best.verification_score < config.min_verification_score
        {
            return Ok(IdentifyResult::Retry(IdentifyRetry {
                reason: IdentifyRetryReason::WeakScore,
                best_hit: Some(best),
            }));
        }
        if hits
            .get(1)
            .is_some_and(|second| best.score - second.score < config.min_margin)
        {
            return Ok(IdentifyResult::Retry(IdentifyRetry {
                reason: IdentifyRetryReason::Ambiguous,
                best_hit: Some(best),
            }));
        }
        Ok(IdentifyResult::Match(IdentifyMatch {
            user_id: best.user_id,
            record_id: best.record_id,
            score: best.score,
            verification_score: best.verification_score,
            votes: best.votes,
        }))
    }

    /// Search record candidates before user collapsing.
    pub fn search_records(
        &self,
        query: &ExtractedTemplate,
        config: SearchConfig,
    ) -> SdkResult<Vec<RecordSearchHit>> {
        let config = config.normalized();
        let query_profile = ExtractorConfig {
            orientation_bins: self.orientation_bins,
            ..ExtractorConfig::default()
        };
        query.validate(query_profile, SdkLimits::default())?;
        let mut candidates = SEARCH_WORKSPACE.with(|workspace| {
            self.collect_record_candidates(query, config, &mut workspace.borrow_mut())
        })?;
        candidates.sort_by(|left, right| {
            compare_hit_values(
                left.score,
                left.votes,
                &self.records[left.record_index].record_id,
                right.score,
                right.votes,
                &self.records[right.record_index].record_id,
            )
        });
        Ok(candidates
            .into_iter()
            .map(|candidate| {
                let record = &self.records[candidate.record_index];
                RecordSearchHit {
                    user_id: record.user_id.clone(),
                    record_id: record.record_id.clone(),
                    votes: candidate.votes,
                    overlap_score: candidate.overlap_score,
                    verification_score: candidate.verification_score,
                    score: candidate.score,
                }
            })
            .collect())
    }

    /// Retrieve token-overlap candidates and geometrically rerank a bounded set.
    ///
    /// Candidate accumulation visits posting lists for query tokens, not every
    /// enrolled record. Tokens common across many users receive lower inverse
    /// document-frequency weight. Only `candidate_limit` records enter the more
    /// expensive minutiae verifier.
    fn collect_record_candidates(
        &self,
        query: &ExtractedTemplate,
        config: SearchConfig,
        workspace: &mut SearchWorkspace,
    ) -> SdkResult<Vec<CandidateHit>> {
        if self.records.is_empty() || query.tokens.is_empty() {
            return Ok(Vec::new());
        }
        let user_count = self
            .records
            .iter()
            .map(|record| record.user_id.as_str())
            .collect::<HashSet<_>>()
            .len()
            .max(1) as f32;
        let unseen_weight = ((user_count + 1.0) / 0.5).ln();
        let mut query_weight = 0.0;
        workspace
            .accumulators
            .resize(self.records.len(), CandidateAccumulator::default());
        workspace.accumulators.fill(CandidateAccumulator::default());
        for token in &query.tokens {
            let Ok(dictionary_index) = self
                .dictionary
                .binary_search_by_key(token, |posting| posting.token)
            else {
                query_weight += unseen_weight;
                continue;
            };
            let posting = &self.dictionary[dictionary_index];
            let idf = posting.idf;
            query_weight += idf;
            let start = posting.start as usize;
            let end = start
                .checked_add(posting.len as usize)
                .ok_or_else(|| SdkError::integrity("posting range overflows usize"))?;
            let list = self
                .postings
                .get(start..end)
                .ok_or_else(|| SdkError::integrity("posting range is outside the index"))?;
            for record_index in list {
                let accumulator = workspace
                    .accumulators
                    .get_mut(*record_index as usize)
                    .ok_or_else(|| SdkError::integrity("posting record is outside the index"))?;
                accumulator.votes = accumulator.votes.saturating_add(1);
                accumulator.weighted_votes += idf;
            }
        }
        let query_weight = query_weight.max(f32::EPSILON);
        let query_tokens = query.tokens.len().max(1) as f32;
        let mut candidates = workspace
            .accumulators
            .iter()
            .copied()
            .enumerate()
            .filter(|(_, accumulator)| accumulator.votes > 0)
            .map(|(record_index, accumulator)| {
                let record_tokens = f32::from(self.records[record_index].token_count).max(1.0);
                let weighted_recall = accumulator.weighted_votes / query_weight;
                let dice =
                    2.0 * f32::from(accumulator.votes) / (query_tokens + record_tokens).max(1.0);
                // Weighted recall rewards coverage of discriminative query
                // evidence. Dice also penalizes very unequal token-set sizes.
                let overlap_score = (weighted_recall * 0.65 + dice * 0.35).clamp(0.0, 1.0);
                CandidateHit {
                    record_index,
                    votes: accumulator.votes,
                    overlap_score,
                    verification_score: 0.0,
                    score: overlap_score,
                }
            })
            .collect::<Vec<_>>();
        let candidate_limit = config
            .rerank
            .candidate_limit
            .max(config.top_k)
            .min(candidates.len());
        let overlap_order = |left: &CandidateHit, right: &CandidateHit| {
            right
                .overlap_score
                .total_cmp(&left.overlap_score)
                .then_with(|| right.votes.cmp(&left.votes))
                .then_with(|| left.record_index.cmp(&right.record_index))
        };
        if candidates.len() > candidate_limit {
            candidates.select_nth_unstable_by(candidate_limit, overlap_order);
            candidates.truncate(candidate_limit);
        }
        candidates.sort_by(overlap_order);
        if config.rerank.enabled {
            let prepared = prepare_query_geometry(
                &query.features,
                self.orientation_bins,
                config.rerank.max_rotation_bins,
            );
            for candidate in &mut candidates {
                candidate.verification_score = verify_template_geometry(
                    &query.features,
                    &prepared,
                    &self.record_features[candidate.record_index],
                    self.orientation_bins,
                    config.rerank,
                    workspace,
                );
                let weight = config.rerank.verification_weight;
                candidate.score = candidate.overlap_score * (1.0 - weight)
                    + candidate.verification_score * weight;
            }
        }
        Ok(candidates)
    }

    /// Validate all index ranges before the index becomes searchable.
    pub fn validate(&self, limits: SdkLimits) -> SdkResult<()> {
        if !(8..=32).contains(&self.orientation_bins) || !self.orientation_bins.is_multiple_of(2) {
            return Err(SdkError::integrity("index orientation bins are invalid"));
        }
        if self.records.len() > limits.max_records {
            return Err(SdkError::resource_limit("index record limit exceeded"));
        }
        if self.records.len() != self.record_features.len() {
            return Err(SdkError::integrity(
                "index record and feature-set counts differ",
            ));
        }
        if self
            .dictionary
            .windows(2)
            .any(|entries| entries[0].token >= entries[1].token)
        {
            return Err(SdkError::integrity(
                "index dictionary is not strictly sorted",
            ));
        }
        let mut record_ids = HashSet::new();
        for record in &self.records {
            validate_identifier("index record_id", &record.record_id)?;
            validate_identifier("index user_id", &record.user_id)?;
            if usize::from(record.token_count) > limits.max_tokens_per_template {
                return Err(SdkError::resource_limit(
                    "index record token limit exceeded",
                ));
            }
            if !record_ids.insert(record.record_id.as_str()) {
                return Err(SdkError::integrity("index contains duplicate record ids"));
            }
        }
        let users = self
            .records
            .iter()
            .map(|record| record.user_id.as_str())
            .collect::<HashSet<_>>()
            .len();
        for record_features in &self.record_features {
            if record_features.features.len() > limits.max_features_per_template {
                return Err(SdkError::resource_limit("index feature limit exceeded"));
            }
            let mut coordinates = HashSet::new();
            for feature in &record_features.features {
                if u32::from(feature.x) >= FEATURE_GRID_W
                    || u32::from(feature.y) >= FEATURE_GRID_H
                    || feature.orientation >= self.orientation_bins
                    || feature.contrast > 7
                    || feature.coherence > 100
                    || !matches!(feature.kind, 1 | 3)
                {
                    return Err(SdkError::integrity("index feature payload is invalid"));
                }
                if !coordinates.insert((feature.x, feature.y)) {
                    return Err(SdkError::integrity(
                        "index feature set contains duplicate coordinates",
                    ));
                }
            }
            let expected =
                RecordFeatures::new(record_features.features.clone(), self.orientation_bins);
            if record_features.orientation_offsets != expected.orientation_offsets
                || record_features.orientation_members != expected.orientation_members
                || record_features.spatial_cells != expected.spatial_cells
                || record_features.spatial_members != expected.spatial_members
            {
                return Err(SdkError::integrity(
                    "index verifier lookups do not match verifier features",
                ));
            }
        }
        let max_postings = limits
            .max_records
            .checked_mul(limits.max_tokens_per_template)
            .ok_or_else(|| SdkError::resource_limit("index posting limit overflows usize"))?;
        if self.dictionary.len() > max_postings || self.postings.len() > max_postings {
            return Err(SdkError::resource_limit(
                "index postings exceed configured limit",
            ));
        }
        let mut expected_start = 0usize;
        let mut posting_counts = vec![0usize; self.records.len()];
        for entry in &self.dictionary {
            let start = entry.start as usize;
            if start != expected_start || entry.len == 0 || entry.user_count == 0 {
                return Err(SdkError::integrity(
                    "index posting ranges are not contiguous and non-empty",
                ));
            }
            let end = start
                .checked_add(entry.len as usize)
                .ok_or_else(|| SdkError::integrity("posting range overflows usize"))?;
            let posting = self
                .postings
                .get(start..end)
                .ok_or_else(|| SdkError::integrity("posting range is outside the index"))?;
            if posting.windows(2).any(|records| records[0] >= records[1]) {
                return Err(SdkError::integrity("posting list is not strictly sorted"));
            }
            if posting
                .iter()
                .any(|record_index| *record_index as usize >= self.records.len())
            {
                return Err(SdkError::integrity("posting references an unknown record"));
            }
            for record_index in posting {
                posting_counts[*record_index as usize] += 1;
            }
            let posting_users = posting
                .iter()
                .map(|record_index| self.records[*record_index as usize].user_id.as_str())
                .collect::<HashSet<_>>()
                .len();
            if posting_users != entry.user_count as usize || posting_users > users {
                return Err(SdkError::integrity("posting user count is inconsistent"));
            }
            let expected_idf = (((users.max(1) as f32) + 1.0) / (posting_users as f32 + 0.5)).ln();
            if !entry.idf.is_finite() || (entry.idf - expected_idf).abs() > 1e-6 {
                return Err(SdkError::integrity(
                    "posting inverse-frequency weight is inconsistent",
                ));
            }
            expected_start = end;
        }
        if expected_start != self.postings.len()
            || posting_counts
                .iter()
                .zip(&self.records)
                .any(|(count, record)| *count != usize::from(record.token_count))
        {
            return Err(SdkError::integrity(
                "index postings do not match record token counts",
            ));
        }
        Ok(())
    }

    /// Index sizing statistics.
    pub fn stats(&self) -> IndexStats {
        IndexStats {
            records: self.records.len(),
            users: self
                .records
                .iter()
                .map(|record| record.user_id.as_str())
                .collect::<HashSet<_>>()
                .len(),
            dictionary_tokens: self.dictionary.len(),
            postings: self.postings.len(),
            average_tokens_per_record: if self.records.is_empty() {
                0.0
            } else {
                self.records
                    .iter()
                    .map(|record| f64::from(record.token_count))
                    .sum::<f64>()
                    / self.records.len() as f64
            },
            average_features_per_record: if self.record_features.is_empty() {
                0.0
            } else {
                self.record_features
                    .iter()
                    .map(|features| features.features.len() as f64)
                    .sum::<f64>()
                    / self.record_features.len() as f64
            },
        }
    }
}

/// Index sizing and density statistics.
#[derive(Debug, Clone, PartialEq, Serialize)]
pub struct IndexStats {
    /// Finger records.
    pub records: usize,
    /// Distinct users.
    pub users: usize,
    /// Distinct descriptor hashes.
    pub dictionary_tokens: usize,
    /// Posting entries.
    pub postings: usize,
    /// Mean descriptors per record.
    pub average_tokens_per_record: f64,
    /// Mean verifier features per record.
    pub average_features_per_record: f64,
}

/// Verify two bounded minutiae sets under bounded rigid transform hypotheses.
///
/// Phase one is a Hough-style transform vote by compatible minutia pairs. Phase
/// two applies the strongest hypothesis and greedily assigns compatible points
/// one-to-one, with one fallback hypothesis for a weak first result. Phase three
/// gates that geometric score by consistency of the pairing's supporting edges.
fn prepare_query_geometry(
    query: &[TemplateFeature],
    orientation_bins: u8,
    max_rotation_bins: u8,
) -> PreparedQuery {
    let rotations = preferred_rotations(max_rotation_bins);
    let mut rotated = Vec::with_capacity(rotations.len() * query.len());
    for rotation in &rotations {
        let angle = f32::from(*rotation) / f32::from(orientation_bins) * std::f32::consts::PI;
        let cosine = angle.cos();
        let sine = angle.sin();
        for feature in query {
            rotated.push(RotatedFeature {
                x: (f32::from(feature.x) * cosine - f32::from(feature.y) * sine).round() as i16,
                y: (f32::from(feature.x) * sine + f32::from(feature.y) * cosine).round() as i16,
                orientation: rotate_orientation(feature.orientation, *rotation, orientation_bins),
            });
        }
    }
    let mut assignment_order = (0..query.len() as u16).collect::<Vec<_>>();
    assignment_order.sort_unstable_by(|left, right| {
        let left = query[usize::from(*left)];
        let right = query[usize::from(*right)];
        right
            .coherence
            .cmp(&left.coherence)
            .then_with(|| (right.kind == 3).cmp(&(left.kind == 3)))
            .then_with(|| right.contrast.cmp(&left.contrast))
    });
    PreparedQuery {
        rotations,
        rotated,
        assignment_order,
    }
}

fn verify_template_geometry(
    query: &[TemplateFeature],
    prepared: &PreparedQuery,
    candidate: &RecordFeatures,
    orientation_bins: u8,
    config: RerankConfig,
    workspace: &mut SearchWorkspace,
) -> f32 {
    if query.is_empty() || candidate.features.is_empty() {
        return 0.0;
    }
    /*
    Verification now keeps several transform hypotheses and scores the edge
    graph induced by each one-to-one minutiae pairing. This tolerates a locally
    imperfect Hough winner and requires mutually consistent distances and
    directions, while retaining hard bounds on rotations, candidates, and
    verifier minutiae.
    */
    let config = config.normalized();
    let max_translation = config.max_translation_units;
    let translation_span = usize::try_from(max_translation * 2 + 1).unwrap_or(1);
    let vote_plane = translation_span * translation_span;
    workspace
        .votes
        .resize(prepared.rotations.len() * vote_plane, 0);
    workspace.votes.fill(0);

    let orientation_tolerance = i16::from(config.orientation_tolerance_bins);
    for rotation_index in 0..prepared.rotations.len() {
        let rotated =
            &prepared.rotated[rotation_index * query.len()..(rotation_index + 1) * query.len()];
        for (query_feature, rotated_feature) in query.iter().zip(rotated) {
            for orientation_offset in -orientation_tolerance..=orientation_tolerance {
                let orientation = (i16::from(rotated_feature.orientation) + orientation_offset)
                    .rem_euclid(i16::from(orientation_bins))
                    as u8;
                for candidate_index in candidate.orientation_bucket(orientation) {
                    let candidate_feature = candidate.features[usize::from(*candidate_index)];
                    if candidate_feature.kind != query_feature.kind
                        || (i16::from(candidate_feature.contrast)
                            - i16::from(query_feature.contrast))
                        .abs()
                            > 3
                    {
                        continue;
                    }
                    let dx = i16::from(candidate_feature.x) - rotated_feature.x;
                    let dy = i16::from(candidate_feature.y) - rotated_feature.y;
                    if dx.abs() > max_translation || dy.abs() > max_translation {
                        continue;
                    }
                    let vote_index = rotation_index * vote_plane
                        + (dy + max_translation) as usize * translation_span
                        + (dx + max_translation) as usize;
                    workspace.votes[vote_index] = workspace.votes[vote_index].saturating_add(1);
                }
            }
        }
    }

    let Some(first) = best_transform_hypothesis(
        &workspace.votes,
        &prepared.rotations,
        vote_plane,
        translation_span,
        max_translation,
    ) else {
        return 0.0;
    };
    let mut best = score_transform_pairing(
        query,
        prepared,
        candidate,
        first,
        orientation_bins,
        config,
        workspace,
    );
    if best >= EARLY_VERIFICATION_SCORE {
        return best;
    }
    suppress_transform_hypothesis(
        &mut workspace.votes,
        first,
        vote_plane,
        translation_span,
        max_translation,
        i16::from(config.position_tolerance_units),
    );
    if let Some(hypothesis) = best_transform_hypothesis(
        &workspace.votes,
        &prepared.rotations,
        vote_plane,
        translation_span,
        max_translation,
    ) {
        best = best.max(score_transform_pairing(
            query,
            prepared,
            candidate,
            hypothesis,
            orientation_bins,
            config,
            workspace,
        ));
    }
    best
}

fn best_transform_hypothesis(
    votes: &[u16],
    rotations: &[i8],
    vote_plane: usize,
    translation_span: usize,
    max_translation: i16,
) -> Option<TransformHypothesis> {
    let (winning_index, support) = votes
        .iter()
        .copied()
        .enumerate()
        .filter(|(_, support)| *support > 0)
        .max_by_key(|(index, support)| {
            let rotation = rotations[*index / vote_plane];
            (*support, -i16::from(rotation).abs())
        })?;
    let rotation_index = winning_index / vote_plane;
    let translation_index = winning_index % vote_plane;
    Some(TransformHypothesis {
        rotation_index,
        dx: (translation_index % translation_span) as i16 - max_translation,
        dy: (translation_index / translation_span) as i16 - max_translation,
        support,
    })
}

fn suppress_transform_hypothesis(
    votes: &mut [u16],
    hypothesis: TransformHypothesis,
    vote_plane: usize,
    translation_span: usize,
    max_translation: i16,
    suppression_radius: i16,
) {
    for offset_y in -suppression_radius..=suppression_radius {
        for offset_x in -suppression_radius..=suppression_radius {
            let suppressed_x = hypothesis.dx + offset_x;
            let suppressed_y = hypothesis.dy + offset_y;
            if suppressed_x.abs() > max_translation || suppressed_y.abs() > max_translation {
                continue;
            }
            let index = hypothesis.rotation_index * vote_plane
                + (suppressed_y + max_translation) as usize * translation_span
                + (suppressed_x + max_translation) as usize;
            votes[index] = 0;
        }
    }
}

fn score_transform_pairing(
    query: &[TemplateFeature],
    prepared: &PreparedQuery,
    candidate: &RecordFeatures,
    hypothesis: TransformHypothesis,
    orientation_bins: u8,
    config: RerankConfig,
    workspace: &mut SearchWorkspace,
) -> f32 {
    workspace.used.resize(candidate.features.len(), false);
    workspace.used.fill(false);
    workspace.pairings.clear();
    let rotated = &prepared.rotated
        [hypothesis.rotation_index * query.len()..(hypothesis.rotation_index + 1) * query.len()];
    let position_tolerance = i16::from(config.position_tolerance_units);
    let distance_limit = i32::from(position_tolerance) * i32::from(position_tolerance) * 2;

    for query_index in &prepared.assignment_order {
        let query_index = usize::from(*query_index);
        let query_feature = query[query_index];
        let rotated_feature = rotated[query_index];
        let target_x = rotated_feature.x + hypothesis.dx;
        let target_y = rotated_feature.y + hypothesis.dy;
        let mut best: Option<(i32, usize)> = None;
        let min_x = (target_x - position_tolerance).max(0);
        let max_x = (target_x + position_tolerance).min(FEATURE_GRID_W as i16 - 1);
        let min_y = (target_y - position_tolerance).max(0);
        let max_y = (target_y + position_tolerance).min(FEATURE_GRID_H as i16 - 1);
        if min_x <= max_x && min_y <= max_y {
            for cell_y in min_y / SPATIAL_CELL_SIZE..=max_y / SPATIAL_CELL_SIZE {
                for cell_x in min_x / SPATIAL_CELL_SIZE..=max_x / SPATIAL_CELL_SIZE {
                    for candidate_index in candidate.spatial_bucket(cell_x, cell_y) {
                        let candidate_index = usize::from(*candidate_index);
                        let candidate_feature = candidate.features[candidate_index];
                        if workspace.used[candidate_index]
                            || candidate_feature.kind != query_feature.kind
                            || i16::from(signed_orientation_delta(
                                candidate_feature.orientation,
                                rotated_feature.orientation,
                                orientation_bins,
                            ))
                            .abs()
                                > i16::from(config.orientation_tolerance_bins)
                            || (i16::from(candidate_feature.contrast)
                                - i16::from(query_feature.contrast))
                            .abs()
                                > 3
                        {
                            continue;
                        }
                        let dx = i16::from(candidate_feature.x) - target_x;
                        let dy = i16::from(candidate_feature.y) - target_y;
                        let distance =
                            i32::from(dx) * i32::from(dx) + i32::from(dy) * i32::from(dy);
                        if distance <= distance_limit
                            && best.is_none_or(|(best_distance, _)| distance < best_distance)
                        {
                            best = Some((distance, candidate_index));
                        }
                    }
                }
            }
        }
        if let Some((_, candidate_index)) = best {
            workspace.used[candidate_index] = true;
            workspace.pairings.push(MinutiaPairing {
                query: query_index as u16,
                candidate: candidate_index as u16,
            });
        }
    }

    let matched = workspace.pairings.len();
    let denominator = (query.len() + candidate.features.len()).max(1) as f32;
    let one_to_one = 2.0 * matched as f32 / denominator;
    let support_ratio = (f32::from(hypothesis.support)
        / query.len().min(candidate.features.len()).max(1) as f32)
        .min(1.0);
    let (edge_support, edge_accuracy) =
        pairing_edge_score(&workspace.pairings, rotated, &candidate.features);
    let base_score = one_to_one * 0.78 + support_ratio * 0.22;
    let edge_consistency = edge_support * 0.60 + edge_accuracy * 0.40;
    (base_score * (0.72 + edge_consistency * 0.28)).clamp(0.0, 1.0)
}

fn pairing_edge_score(
    pairings: &[MinutiaPairing],
    rotated_query: &[RotatedFeature],
    candidate: &[TemplateFeature],
) -> (f32, f32) {
    const MIN_EDGE_LENGTH: f32 = 4.0;
    const MIN_ANGLE_COSINE: f32 = 0.965_925_8; // 15 degrees.
    let mut eligible = 0usize;
    let mut supporting = 0usize;
    let mut accuracy = 0.0;
    'edges: for left in 0..pairings.len() {
        for right in left + 1..pairings.len() {
            if eligible >= MAX_SUPPORTING_EDGE_CHECKS {
                break 'edges;
            }
            let left_pair = pairings[left];
            let right_pair = pairings[right];
            let query_left = rotated_query[usize::from(left_pair.query)];
            let query_right = rotated_query[usize::from(right_pair.query)];
            let candidate_left = candidate[usize::from(left_pair.candidate)];
            let candidate_right = candidate[usize::from(right_pair.candidate)];
            let query_dx = f32::from(query_right.x - query_left.x);
            let query_dy = f32::from(query_right.y - query_left.y);
            let candidate_dx = f32::from(candidate_right.x) - f32::from(candidate_left.x);
            let candidate_dy = f32::from(candidate_right.y) - f32::from(candidate_left.y);
            let query_length = query_dx.hypot(query_dy);
            let candidate_length = candidate_dx.hypot(candidate_dy);
            if query_length < MIN_EDGE_LENGTH || candidate_length < MIN_EDGE_LENGTH {
                continue;
            }
            eligible += 1;
            let length_tolerance = 3.0 + query_length * 0.12;
            let length_error = (candidate_length - query_length).abs();
            let cosine = ((query_dx * candidate_dx + query_dy * candidate_dy)
                / (query_length * candidate_length))
                .clamp(-1.0, 1.0);
            if length_error <= length_tolerance && cosine >= MIN_ANGLE_COSINE {
                supporting += 1;
                let length_accuracy = 1.0 - length_error / length_tolerance;
                let angle_accuracy =
                    ((cosine - MIN_ANGLE_COSINE) / (1.0 - MIN_ANGLE_COSINE)).clamp(0.0, 1.0);
                accuracy += (length_accuracy + angle_accuracy) * 0.5;
            }
        }
    }
    if eligible == 0 {
        return (1.0, 1.0);
    }
    (
        supporting as f32 / eligible as f32,
        if supporting == 0 {
            0.0
        } else {
            accuracy / supporting as f32
        },
    )
}

/// Search zero first, then alternating negative/positive rotations by magnitude.
fn preferred_rotations(max_rotation: u8) -> Vec<i8> {
    let mut rotations = vec![0];
    for magnitude in 1..=max_rotation as i8 {
        rotations.push(-magnitude);
        rotations.push(magnitude);
    }
    rotations
}

/// Apply a global rotation to an axial orientation with circular wrapping.
fn rotate_orientation(orientation: u8, rotation: i8, bins: u8) -> u8 {
    (i16::from(orientation) + i16::from(rotation)).rem_euclid(i16::from(bins)) as u8
}

/// Return the shortest signed distance between two circular orientation bins.
fn signed_orientation_delta(candidate: u8, query: u8, bins: u8) -> i8 {
    let bins = i16::from(bins);
    let mut delta = i16::from(candidate) - i16::from(query);
    while delta > bins / 2 {
        delta -= bins;
    }
    while delta < -(bins / 2) {
        delta += bins;
    }
    delta as i8
}

fn compare_hit_values(
    left_score: f32,
    left_votes: u16,
    left_key: &str,
    right_score: f32,
    right_votes: u16,
    right_key: &str,
) -> std::cmp::Ordering {
    right_score
        .total_cmp(&left_score)
        .then_with(|| right_votes.cmp(&left_votes))
        .then_with(|| left_key.cmp(right_key))
}

fn finite_unit(value: f32, fallback: f32) -> f32 {
    if value.is_finite() {
        value.clamp(0.0, 1.0)
    } else {
        fallback
    }
}

#[cfg(test)]
mod tests {
    use super::super::extractor::FingerRecord;
    use super::*;

    #[test]
    fn collapses_hits_by_user() {
        let templates = vec![
            template("A_1", "A", &[1, 2, 3]),
            template("A_2", "A", &[9, 10]),
            template("B_1", "B", &[1, 4]),
        ];
        let index = BiometricIndex::build(&templates).unwrap();
        let query = template("Q", "Q", &[1, 2]);
        let hits = index.search_users(&query, SearchConfig::default()).unwrap();
        assert_eq!(hits[0].user_id, "A");
        assert_eq!(hits[0].record_id, "A_1");
    }

    #[test]
    fn common_tokens_remain_searchable_in_a_small_one_user_index() {
        let templates = vec![template("A_1", "A", &[1, 2]), template("A_2", "A", &[1, 2])];
        let index = BiometricIndex::build(&templates).unwrap();
        let hits = index
            .search_users(&template("Q", "Q", &[1, 2]), SearchConfig::default())
            .unwrap();
        assert_eq!(hits.len(), 1);
        assert_eq!(hits[0].user_id, "A");
    }

    #[test]
    fn reranker_accepts_consistent_translation() {
        let candidate = template_with_features(
            "A_1",
            "A",
            &[1, 2, 3],
            &[feature(6, 4, 3), feature(7, 4, 3), feature(8, 5, 4)],
        );
        let distractor = template_with_features(
            "B_1",
            "B",
            &[1, 2, 3],
            &[feature(1, 1, 9), feature(15, 12, 2), feature(18, 20, 7)],
        );
        let index = BiometricIndex::build(&[candidate, distractor]).unwrap();
        let query = template_with_features(
            "Q",
            "Q",
            &[1, 2, 3],
            &[feature(4, 4, 3), feature(5, 4, 3), feature(6, 5, 4)],
        );
        let hits = index.search_users(&query, SearchConfig::default()).unwrap();
        assert_eq!(hits[0].user_id, "A");
        assert!(hits[0].verification_score > hits[1].verification_score);
    }

    #[test]
    fn reranker_rewards_a_consistent_supporting_edge_graph() {
        let query = vec![
            feature(10, 10, 3),
            feature(30, 10, 3),
            feature(10, 30, 5),
            feature(30, 30, 5),
            feature(20, 20, 4),
        ];
        let consistent = query
            .iter()
            .map(|feature| TemplateFeature {
                x: feature.x + 5,
                y: feature.y + 4,
                ..*feature
            })
            .collect::<Vec<_>>();
        let distorted_offsets = [(-3i16, 0i16), (3, 0), (0, -3), (0, 3), (2, -2)];
        let distorted = query
            .iter()
            .zip(distorted_offsets)
            .map(|(feature, (dx, dy))| TemplateFeature {
                x: (i16::from(feature.x) + 5 + dx) as u8,
                y: (i16::from(feature.y) + 4 + dy) as u8,
                ..*feature
            })
            .collect::<Vec<_>>();
        let config = RerankConfig::default();
        let prepared = prepare_query_geometry(&query, 16, config.max_rotation_bins);
        let mut workspace = SearchWorkspace::default();
        let consistent_score = verify_template_geometry(
            &query,
            &prepared,
            &RecordFeatures::new(consistent, 16),
            16,
            config,
            &mut workspace,
        );
        let distorted_score = verify_template_geometry(
            &query,
            &prepared,
            &RecordFeatures::new(distorted, 16),
            16,
            config,
            &mut workspace,
        );
        assert!(
            consistent_score > distorted_score + 0.05,
            "consistent={consistent_score}, distorted={distorted_score}"
        );
    }

    #[test]
    fn standalone_index_rejects_duplicate_records_and_posting_gaps() {
        let templates = vec![template("A_1", "A", &[1, 2]), template("B_1", "B", &[3, 4])];
        let index = BiometricIndex::build(&templates).unwrap();

        let mut duplicate = index.clone();
        duplicate.records[1].record_id = duplicate.records[0].record_id.clone();
        assert_eq!(
            duplicate.validate(SdkLimits::default()).unwrap_err().code(),
            super::super::error::SdkErrorCode::Integrity
        );

        let mut gap = index;
        gap.dictionary[0].start = 1;
        assert_eq!(
            gap.validate(SdkLimits::default()).unwrap_err().code(),
            super::super::error::SdkErrorCode::Integrity
        );
    }

    #[test]
    fn malformed_query_template_is_rejected_before_search() {
        let index = BiometricIndex::build(&[template("A_1", "A", &[1, 2])]).unwrap();
        let mut query = template("Q", "Q", &[1, 2]);
        query.features[0].orientation = index.orientation_bins;

        assert_eq!(
            index
                .search_users(&query, SearchConfig::default())
                .unwrap_err()
                .code(),
            super::super::error::SdkErrorCode::Integrity
        );
    }

    #[test]
    fn identify_quality_is_bounded_to_the_score_range() {
        let normalized = IdentifyConfig {
            min_quality: u8::MAX,
            ..IdentifyConfig::default()
        }
        .normalized();
        assert_eq!(normalized.min_quality, 100);
    }

    fn template(record_id: &str, user_id: &str, tokens: &[u64]) -> ExtractedTemplate {
        template_with_features(record_id, user_id, tokens, &[feature(2, 2, 3)])
    }

    fn template_with_features(
        record_id: &str,
        user_id: &str,
        tokens: &[u64],
        features: &[TemplateFeature],
    ) -> ExtractedTemplate {
        ExtractedTemplate {
            record: FingerRecord {
                record_id: record_id.to_owned(),
                user_id: user_id.to_owned(),
            },
            quality: 90,
            token_count: tokens.len() as u16,
            tokens: tokens.to_vec(),
            features: features.to_vec(),
        }
    }

    fn feature(x: u8, y: u8, orientation: u8) -> TemplateFeature {
        TemplateFeature {
            x,
            y,
            orientation,
            contrast: 4,
            coherence: 80,
            kind: 1,
        }
    }
}
