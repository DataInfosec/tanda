# Matcher Performance

## Measurement Scope

Measurements were taken on 2026-07-11 with:

| Item | Value |
| --- | --- |
| CPU | Intel Core 7 150U, 6 logical CPUs exposed |
| OS | Linux 6.6 under WSL2, x86_64 |
| Compiler | Rust 1.96.1 |
| Build | `--release` |
| Capture format | `400x500`, 8-bit grayscale |
| Canonical corpus | 1,269 unique templates, 1,097 inferred users |
| Population-size benchmark | First 1,000 templates |

The corpus is `data/root/raw`. Filename suffixes `_1` and `_2` represent
different thumbs from the same person, not repeated impressions of one finger.
Exact and translated probes test implementation stability and search cost.
Held-out different-thumb probes provide preliminary impostor separation. They
do not establish production FAR, FRR, or equal-error rate.

These measurements cover extraction and matching. The current SDK-owned libSQL
replica was introduced after the storage benchmark harness, so this report does
not claim a current database size, WAL transfer size, bootstrap latency, or
Android synchronization cost.

## Current 1,000-Template Result

The current reranker uses reusable workspaces, precomputed IDF and spatial
lookups, adaptive transform hypotheses, and a supporting-edge consistency gate.

| Measurement | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| Raw extraction | 3.89 ms | 6.56 ms | 8.28 ms |
| Prepared-template top-1 search | 2.49 ms | 3.45 ms | 4.43 ms |
| Raw extraction plus top-1 search | 7.08 ms | 9.75 ms | 11.18 ms |

| Build measurement | Result |
| --- | ---: |
| Derived index bytes | 3,759,633 bytes (3.59 MiB) |
| Index build | 124-134 ms |

The encoded index figure measures the lower-level `BiometricIndex` codec. The
attendance facade does not persist that encoding; it derives and publishes the
matcher from validated libSQL rows.

## Decision Regression

Current decision regression on all 1,269 canonical captures:

| Probe | Expected top-1 | Accepted correct | Retry | Accepted wrong |
| --- | ---: | ---: | ---: | ---: |
| Exact capture | 1,269 | 1,266 | 3 | 0 |
| Translate x by 10 px | 1,266 | 1,245 | 24 | 0 |
| Translate x by 20 px | 1,269 | 1,266 | 3 | 0 |
| Translate x by 40 px | 1,269 | 1,266 | 3 | 0 |
| Translate y by 20 px | 1,269 | 1,266 | 3 | 0 |

The held-out split contained 634 non-identical, different-finger probes after
exact byte duplicates were excluded. The strongest impostor score had median
`0.1482`, p95 `0.2029`, p99 `0.2211`, and maximum `0.2485`. The default `0.30`
score floor accepted `0 / 634`.

Supporting-edge evidence gates the previous geometric score and cannot increase
it, which preserves this measured separation. This is regression evidence, not
a claim of zero false acceptance in deployment.

See [SourceAFIS comparison](sourceafis-comparison.md) for results from the same
host and candidate sizes.

## Earlier Baseline

The earlier 1,000-template implementation produced:

| Probe | Match p50 | Match p95 | End-to-end p50 | End-to-end p95 | End-to-end p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Exact capture | 1.60 ms | 3.65 ms | 4.66 ms | 9.67 ms | 17.73 ms |
| Translate x by 10 px | 1.91 ms | 3.45 ms | 5.77 ms | 9.72 ms | 12.84 ms |
| Translate x by 20 px | 1.70 ms | 3.49 ms | 5.30 ms | 10.23 ms | 18.82 ms |
| Translate x by 40 px | 1.89 ms | 3.72 ms | 5.79 ms | 11.16 ms | 18.60 ms |
| Translate y by 20 px | 1.84 ms | 3.38 ms | 5.45 ms | 9.31 ms | 14.10 ms |

`Match` is candidate generation plus geometric reranking. `End-to-end` also
includes raw-image extraction and final decision policy. The current verifier
does more work than this baseline, while remaining within a low host latency at
fixed-population galleries.

## Enrollment Quality

The default enrollment quality floor is `65`. Recalculated on the 1,269 unique
canonical captures:

| Threshold | Accepted captures | Acceptance | Users with both captures accepted |
| --- | ---: | ---: | ---: |
| 65 | 1,262 / 1,269 | 99.4% | 170 / 172 (98.8%) |
| 70 | 1,217 / 1,269 | 95.9% | 158 / 172 (91.9%) |
| 75 | 1,023 / 1,269 | 80.6% | 119 / 172 (69.2%) |

Visual review showed that the seven captures below 65 were small, sparse,
washed out, overly dark, or noisy. The 65-69 band included many usable
impressions, which is why 70 is unnecessarily strict for this corpus.

## Capacity Interpretation

The default hard limit is 4,096 finger records. It is an input-safety bound, not
a deployment recommendation. A 1,000-template index approximates 500 subjects
when two fingers are enrolled per subject and is the current planning target for
one fixed-population gallery.

Index build cost is paid after open, accepted local enrollment, or a sync that
changes effective templates. Clock-in uses the already published immutable
index. A production capacity test should measure the complete population distribution
instead of repeating templates under synthetic identifiers, because posting
frequency and duplicate structure affect index memory and candidate cost.

## Android Validation Plan

The crate and UniFFI facade pass compilation checks for Android targets, but no
representative device was connected for this run. Host milliseconds cannot be
used as Android service-level objectives.

Measure at least one low-end and one typical target with:

- a release ARM64 library and production sensor path;
- 250, 500, and 1,000 real templates;
- p50, p95, and p99 over several hundred scans;
- process RSS before and after matcher publication;
- first bootstrap, no-change sync, and changed-gallery sync time;
- local enrollment transaction and matcher rebuild time;
- JNI/JNA byte-copy cost;
- thermal behavior under repeated extraction;
- airplane-mode restart and identification.

The current host result leaves useful algorithmic headroom, but biometric
calibration and device measurements remain release blockers.
