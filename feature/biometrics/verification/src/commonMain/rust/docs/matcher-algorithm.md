# Fingerprint Matcher Algorithm

This document explains how the SDK turns one `400x500` grayscale fingerprint
capture into a searchable template and how it performs a fast 1:N identity
lookup. It is written alongside the implementation in
[`src/sdk/extractor.rs`](../src/sdk/extractor.rs) and
[`src/sdk/index.rs`](../src/sdk/index.rs).

The matcher is a two-stage minutiae matcher:

1. Translation- and rotation-tolerant descriptor hashes retrieve a small set of
   candidate finger records from an inverted index.
2. A geometric verifier compares the actual minutiae of only those candidates.

The descriptor stage is deliberately permissive. A hash collision or common
local ridge pattern can nominate a candidate, but it cannot by itself accept a
user. The geometric stage and the final decision policy provide that check.

## Terminology And Units

- A **ridge ending** is a skeleton ridge pixel with one connected ridge branch.
- A **bifurcation** is a skeleton ridge pixel with three connected ridge
  branches.
- A **minutia** is one ridge ending or bifurcation. **Minutiae** is the plural.
- Full-resolution image coordinates are `400x500` pixels.
- Minutiae coordinates use a half-resolution `200x250` grid. One coordinate
  unit therefore represents two source-image pixels.
- Ridge orientation is axial: `theta` and `theta + 180 degrees` describe the
  same ridge direction. The default profile quantizes 180 degrees into 16 bins,
  so one bin is 11.25 degrees.

## End-To-End Flow

```mermaid
flowchart LR
    raw["400x500 grayscale capture"] --> field["Ridge field and block quality"]
    raw --> half["2x downsample"]
    half --> binary["Adaptive binarization"]
    binary --> clean["Cleanup and thinning"]
    clean --> cn["Crossing-number minutiae"]
    field --> cn
    cn --> descriptors["Invariant pair and triplet tokens"]
    cn --> verifier["Distributed verifier minutiae"]
    descriptors --> lookup["Inverted-index candidate lookup"]
    lookup --> shortlist["Bounded shortlist"]
    verifier --> geometry["Rotation and translation vote"]
    shortlist --> geometry
    geometry --> one["One-to-one minutiae verification"]
    one --> user["Best finger record per user"]
    user --> policy["Match or Retry policy"]
```

The raw image is discarded after extraction. A template stores descriptor
hashes, up to 64 verifier minutiae, a quality score, and internal ownership
metadata. No raw or thinned image is stored.

## Implementation Map

| Function | Role | Important design choice |
| --- | --- | --- |
| `extract_raw_fingerprint` | Coordinates extraction | Produces tokens and verifier minutiae from the same filtered features |
| `block_features` | Builds usable ridge-block map | Rejects flat, weak, and incoherent regions before minutiae extraction |
| `block_stats` | Calculates contrast and structure tensor | Stable block orientation instead of tracing individual ridges |
| `downsample_half` | Creates `200x250` work image | Four-pixel average reduces noise and skeleton work |
| `adaptive_binary` | Separates ridges from local background | Integral-image local mean gives constant-time windows |
| `cleanup_binary` | Removes isolated specks and tiny holes | Conservative rules avoid manufacturing ridge branches |
| `thin_zhang_suen` | Produces one-pixel ridge skeleton | Connectivity-preserving, convergent, and iteration-bounded |
| `neighbors` | Reads `P2..P9` around a pixel | Fixed array avoids per-pixel allocation |
| `transitions` | Counts circular `0 -> 1` changes | Implements the crossing-number primitive |
| `crossing_number` | Classifies a skeleton crossing | `1` is ending; `3` is bifurcation |
| `minutiae_features` | Filters and annotates crossings | Border guard, ridge-block qualification, and spatial suppression |
| `tokens_from_features` | Creates retrieval evidence | Local pair and triplet relationships tolerate translation and rotation |
| `pair_descriptor` | Quantizes one local relationship | Relative geometry plus kind and local quality fields |
| `select_tokens` | Bounds candidate descriptors | Deduplicates, quality-ranks, then sorts at most 512 hashes |
| `select_verifier_features` | Bounds geometric features | Keeps the strongest minutia per spatial cell, at most 64 |
| `quality_from_features` | Scores capture usability | Combines image, ridge-flow, and plausible-minutiae evidence |
| `BiometricIndex::build_with_config` | Builds dictionary and postings | Persists user frequency for descriptor weighting |
| `collect_record_candidates` | Retrieves a record shortlist | IDF-weighted overlap, then a bounded verifier set |
| `verify_template_geometry` | Verifies template geometry | Adaptive transform hypotheses, one-to-one pairing, and supporting-edge gate |
| `search_users` | Converts record hits to identities | Keeps only the best enrolled finger for each user |
| `identify_user` | Applies acceptance policy | Returns `Retry` instead of blindly accepting rank one |

## 1. Capture Boundary And Orientation

`RawFingerprint::from_bytes` requires exactly 200,000 bytes: one unsigned
grayscale byte per pixel. Extraction vertically flips the current sensor image
into matcher coordinates. Keeping this conversion at the boundary ensures that
enrollment and identification see the same orientation.

An integration using a sensor with a different size, byte layout, or orientation
must normalize it before this extractor. Mixing orientations between enrollment
and identification consumes the verifier's rotation allowance and can prevent a
match.

## 2. Ridge Field And Usable Blocks

`block_features` divides the full-resolution image into `20x20` pixel blocks,
forming a `20x25` grid. `block_stats` calculates three kinds of evidence for
each block:

- pixel standard deviation, used as local contrast;
- gradient energy, used to reject flat areas;
- directional coherence, used to reject blocks without a stable ridge flow.

For central-difference gradients `gx` and `gy`, the block accumulates the
structure tensor terms:

```text
Gxx = sum(gx * gx)
Gyy = sum(gy * gy)
Gxy = sum(gx * gy)
```

The axial ridge orientation is:

```text
theta = 0.5 * atan2(2 * Gxy, Gxx - Gyy)
```

and coherence is:

```text
sqrt((Gxx - Gyy)^2 + 4 * Gxy^2) / (Gxx + Gyy)
```

Coherence approaches 1 when gradients in the block agree on a dominant
direction and approaches 0 for flat or directionally random content. Under the
default profile, a block must have standard deviation at least `4.0`, gradient
energy at least `20.0`, and coherence at least `0.18`.

The accepted block map serves two purposes. It contributes to capture quality,
and it qualifies skeleton crossings so minutiae in flat background or noisy
regions are ignored.

## 3. Half-Resolution Ridge Skeleton

Minutiae extraction runs on a `200x250` image to reduce work and memory while
retaining enough spatial precision for clock-in matching.

### Downsampling

`downsample_half` averages every `2x2` source-pixel square. Averaging provides a
small low-pass filter and is more stable than selecting one of the four pixels.

### Adaptive binarization

`adaptive_binary` uses an integral image to calculate an `11x11` local mean in
constant time per output pixel. A pixel becomes ridge foreground when:

```text
pixel < 245 AND pixel + 6 < local_mean
```

The local threshold handles gradual illumination variation better than one
global threshold. The `pixel < 245` condition prevents a nearly white image from
turning tiny numerical differences into ridges.

### Binary cleanup

`cleanup_binary` performs two bounded passes:

- a ridge pixel with at most one ridge neighbor is removed;
- a background pixel with at least seven ridge neighbors is filled.

This removes isolated specks and closes one-pixel holes before thinning. It is
intentionally conservative because aggressive morphology can join separate
ridges and manufacture bifurcations.

### Zhang-Suen thinning

`thin_zhang_suen` reduces thick binary ridges to a one-pixel-wide skeleton while
preserving local connectivity. Each iteration has the two standard directional
subpasses. A ridge pixel is considered only when it has 2 through 6 ridge
neighbors and exactly one background-to-ridge transition around its eight
neighbors. Subpass-specific connectivity conditions prevent deletion from
breaking the ridge.

The loop stops when neither subpass changes the image and is capped at 32
iterations. The cap makes extraction time bounded even for pathological input.

The implementation uses one byte per skeleton pixel rather than a packed
boolean container. This consumes about 50 KiB for the half-resolution image but
makes the repeated neighborhood scans simpler and faster.

## 4. Crossing-Number Minutiae

`neighbors` returns the eight pixels around a skeleton point clockwise, starting
at north:

```text
P9 P2 P3
P8 P1 P4
P7 P6 P5
```

`transitions` counts `0 -> 1` changes while walking `P2, P3, ..., P9, P2`.
For a one-pixel skeleton, this is the crossing number:

```text
crossing number = 1  -> ridge ending
crossing number = 3  -> bifurcation
other values         -> ordinary ridge point or artifact
```

`minutiae_features` applies additional controls before retaining a crossing:

1. Ignore an eight-unit border, equivalent to 16 source pixels. Cropped ridges
   otherwise create false endings at the image boundary.
2. Require the crossing to fall inside an accepted full-resolution ridge block.
3. Attach the block's ridge orientation, contrast, coherence, and energy.
4. Rank candidates by energy, contrast, coherence, and a small bifurcation
   bonus.
5. Apply non-maximum suppression with a five-unit radius, equivalent to 10
   source pixels, so a small skeleton defect cannot create a dense cluster of
   minutiae.
6. Retain at most three times the configured verifier-feature limit before the
   later spatial sampling stage.

The minutia orientation comes from the local ridge field rather than a single
skeleton branch. The field is less sensitive to one-pixel thinning changes and
gives enrollment and query templates a more stable angular reference.

## 5. Invariant Candidate Descriptors

Absolute minutia coordinates change when a finger is placed elsewhere on the
sensor, so they are not suitable index keys. `tokens_from_features` instead
describes local relationships.

For each minutia anchor, it selects up to six nearest minutiae within 80
half-resolution units. `pair_descriptor` quantizes:

```text
distance between anchor and neighbor
neighbor bearing relative to the anchor ridge orientation
neighbor ridge orientation relative to the anchor orientation
anchor minutia kind
neighbor minutia kind
minimum local contrast
minimum local coherence
```

Translation disappears because only a coordinate difference is used. Global
rotation mostly disappears because bearing and orientation are expressed
relative to the anchor's ridge orientation. Quantization absorbs small capture
and extraction differences.

Each anchor-neighbor pair produces a pair token. The two nearest neighbors also
produce one triplet token that combines both local relationships. Pair tokens
provide recall when one neighboring minutia is missing; triplet tokens are more
specific and reduce collisions between common local patterns.

`token_hash` converts the quantized bytes to a deterministic 64-bit FNV-1a hash
with a pair/triplet domain byte. This is not a cryptographic hash and is not used
for integrity. A hash match is only candidate evidence and must pass geometric
verification.

`select_tokens` deduplicates equal hashes, retains the strongest observation of
each descriptor, keeps at most 512 tokens, and sorts the result. The sorted
representation supports compact persistence and binary-search index lookup.

## 6. Distributed Verifier Minutiae

The second stage needs actual coordinates. Keeping every detected crossing would
increase artifact and index size and allow one noisy patch to dominate
verification.

`select_verifier_features` divides the half-resolution image into `12x12` cells,
keeps the strongest minutia in each occupied cell, ranks those representatives,
and retains at most 64. This produces a compact, spatially distributed set of:

```text
(x, y, orientation, contrast, coherence, kind)
```

The candidate tokens and verifier minutiae come from the same filtered crossing
set, but they serve different jobs: tokens retrieve quickly; coordinates verify
geometry.

## 7. Capture Quality

`quality_from_features` produces an SDK-specific score from 0 through 100. It is
not NFIQ2 and should not be compared numerically with another SDK's quality
scale.

The base score combines:

| Signal | Weight | Purpose |
| --- | ---: | --- |
| Usable ridge-block coverage | 20% | Reject tiny or overwhelmingly active captures |
| Foreground fraction | 15% | Reject empty or nearly filled frames |
| Mean directional coherence | 30% | Reward stable ridge flow |
| Orientation diversity | 20% | Reject artificial parallel stripes |
| Mean contrast | 10% | Reward visible ridge separation |
| Dark-saturation control | 5% | Penalize crushed black areas |

Minutiae count contributes a further calibrated component. Fewer than six
minutiae caps quality at 30; too few or implausibly many minutiae reduce the
score. Additional strong penalties cover extremely concentrated orientation and
high-foreground, low-coherence noise.

Enrollment defaults to quality `65`; identification defaults to `40`. Enrollment
protects the long-lived database, while identification can ask for another scan
without persisting a marginal template. Sensor-specific thresholds should be
calibrated with repeated captures from the actual device.

## 8. Inverted-Index Candidate Retrieval

`BiometricIndex::build_with_config` creates a sorted dictionary:

```text
descriptor token -> posting list of finger-record indexes
```

Each dictionary entry also stores how many distinct users contain the token.
This distinction matters because two templates for one user should not make a
descriptor appear globally common twice.

At query time, `collect_record_candidates` binary-searches each query token and
visits only its posting list. A matched token receives inverse-user-frequency
weight:

```text
idf = ln((number_of_users + 1) / (users_with_token + 0.5))
```

Common ridge relationships therefore contribute less than uncommon ones. For
each record:

```text
weighted_recall = matched_idf / total_query_idf
dice            = 2 * shared_token_count
                  / (query_token_count + record_token_count)
overlap_score   = 0.65 * weighted_recall + 0.35 * dice
```

Only the highest overlap candidates proceed to verification. Identification
uses eight candidates by default, so geometric work does not grow linearly with
every enrolled finger. The postings lookup still grows with the frequency of
the query's descriptors, which is why IDF weighting and discriminative triplet
tokens are important.

## 9. Geometric Minutiae Verification

`verify_template_geometry` has three bounded phases. Query rotations are
calculated once before any candidate is verified. Candidate orientation and
coarse spatial buckets are derived once when the index is built.

### Transform voting

The verifier searches a small set of global rotations. With the defaults it
tries `0, -1, +1, -2, +2` orientation bins, or `0` and approximately `+/-11.25`
and `+/-22.5` degrees.

For each rotation, compatible query/candidate minutia pairs vote for a
translation `(dx, dy)`. Pairs must have the same minutia kind, compatible
orientation, and similar contrast. Translation is limited to `+/-32`
half-resolution units, equivalent to `+/-64` source pixels.

Candidate minutiae are bucketed by orientation before voting. This avoids
testing every query minutia against every candidate minutia for every rotation.
The strongest transform is evaluated first, preferring a smaller absolute
rotation on equal support. If its verification score is below `0.72`, the local
neighborhood around that vote is suppressed and one second transform hypothesis
is evaluated. A strong first transform therefore retains the fast path.

### One-to-one agreement

Each selected transform is applied to every query minutia. Higher-coherence
features are assigned first. A transformed
query point may match an unused candidate point when:

- positions differ by at most three half-resolution units on each axis;
- ridge orientations differ by at most one bin;
- minutia kinds are equal;
- contrast buckets differ by at most three.

Candidate lookup uses sparse `8x8` spatial cells rather than allocating a full
`200x250` grid for every comparison. The nearest compatible point is selected
and marked used. Marking it used is critical: one candidate bifurcation cannot
satisfy several query bifurcations and artificially inflate the score.

### Supporting-edge consistency

The one-to-one pairing induces an edge graph. Up to 256 pairs of matched
minutiae are checked. An edge supports the pairing when:

- its query and candidate directions differ by no more than 15 degrees;
- its length error is within `3 + 12%` half-resolution units;
- both endpoints already belong to the one-to-one pairing.

`edge_support` is the fraction of checked edges that pass. `edge_accuracy`
captures how close the passing lengths and directions are. These values gate the
previous geometric score instead of being added as independent similarity:

```text
one_to_one   = 2 * matched_minutiae / (query_minutiae + candidate_minutiae)
support      = transform_votes / min(query_minutiae, candidate_minutiae)
base         = 0.78 * one_to_one + 0.22 * min(support, 1)
edge_gate    = 0.72 + 0.28 * (0.60 * edge_support + 0.40 * edge_accuracy)
verification = base * edge_gate
```

The gate is always at most 1. A coherent supporting graph preserves the old
geometric score; an inconsistent graph can only lower it. This property is
important because edge coincidences must never manufacture an unrelated match.

The final record score blends retrieval and verification:

```text
score = 0.32 * overlap_score + 0.68 * verification_score
```

The retrieval score keeps useful descriptor evidence, while the larger verifier
weight ensures that geometric agreement dominates acceptance.

## 10. User-Level Decision

Search operates on independent finger records. `search_users` keeps only the
strongest finger record for each `user_id`, then sorts users. This is why the
public API does not need `_1`, `_2`, left-thumb, or right-thumb labels.

`identify_user` returns `Match` only when all default checks pass:

```text
query quality >= 40
final score >= 0.30
geometric verification >= 0.20
best user score - second user score >= 0.02
```

Otherwise it returns a typed `Retry`: low quality, no candidates, weak score, or
ambiguous users. Ranking first is not enough on its own because even unrelated
prints have a strongest candidate in a finite index.

## Performance-Oriented Improvements

The current design keeps the expensive work small and bounded:

- Minutiae extraction runs at half resolution, reducing the skeleton pixel count
  by 75%.
- Integral-image thresholding makes each local-mean lookup constant time.
- A byte grid and fixed-size neighbor arrays avoid allocation in pixel-neighbor
  operations.
- Thinning has a convergence check and a hard iteration limit.
- Border rejection, ridge-block qualification, and spatial suppression remove
  artifacts before descriptor construction.
- Pair and triplet descriptors provide translation/rotation-tolerant index keys
  instead of comparing every template geometrically.
- User-frequency IDF is precomputed and reduces the impact of common descriptors.
- Linear-time bounded selection retains only the reranking shortlist.
- Query rotations are computed once per search rather than once per candidate.
- Orientation and sparse spatial buckets prune verifier candidates.
- The transform vote plane is fixed by configured rotation/translation bounds.
- A strong first transform skips the second hypothesis.
- Supporting-edge consistency strengthens the pairing without adding similarity.
- Spatially distributed verifier minutiae limit storage and stop dense local
  artifacts from dominating.
- One-to-one assignment prevents duplicate geometric credit.
- Thread-local extraction and search workspaces reuse large buffers without
  putting mutable scratch state into synchronized gallery rows.
- The loaded in-memory index is reusable across clock-ins; raw images and
  templates are not reparsed for every search.

These improvements produced the measurements in
[`performance.md`](performance.md). They do not change the requirement to run
device benchmarks and biometric calibration before production deployment.

## Complexity And Bounds

Let `Tq` be query tokens, `P(t)` the posting-list length for token `t`, `C` the
bounded candidate count, `R` searched rotations, and `Mq`/`Mc` verifier minutiae.

```text
candidate lookup: O(sum(P(t)) for t in query)
shortlist:        expected O(H) selection + O(C log C) final ordering
transform vote:  O(C * R * Mq * compatible_orientation_candidates)
assignment:      O(C * hypotheses * Mq * nearby_spatial_candidates)
edge graph:      O(C * hypotheses * min(matched^2, 256))
```

Default hard working sets are 512 tokens, 64 verifier minutiae, 8 identification
candidates, 5 rotations, a `65x65` translation plane per rotation, and a `7x7`
assignment window. These bounds are why matching remains practical on Android at
location-scale indexes.

## Known Limitations

- The current calibration corpus contains different thumbs, not repeated
  impressions of each physical finger. Synthetic translation and impostor tests
  are regression evidence, not certified FAR/FRR results.
- The verifier models one rigid rotation and translation. It does not yet model
  elastic skin deformation, pressure changes, or partial-overlap transforms.
- The extractor does not currently use ridge-frequency enhancement, core/delta
  detection, learned embeddings, or NFIQ2.
- Quantized descriptors intentionally trade precision for retrieval recall.
  Geometric verification is therefore mandatory for acceptance.
- Quality and matcher scores are internal scales, not probabilities and not
  interchangeable with scores from another biometric SDK.
- Templates are sensitive biometric data even though raw images are discarded.
  Storage encryption and authenticated synchronization remain application or
  platform responsibilities.

Any algorithm change that alters minutiae, token quantization, orientation bins,
or score calibration requires rebuilding class matchers and rerunning corpus
and device benchmarks.
