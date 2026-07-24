# SourceAFIS Comparison

This report compares the SDK's custom Rust matcher with SourceAFIS for Java
3.18.1 on the operations that can be measured fairly with the available
fingerprint corpus. It separates speed, size, algorithm design, and accuracy
evidence because winning one category does not imply winning the others.

## Summary

For the intended offline school-location workload, the custom SDK is decisively
faster:

- At 1,000 templates, prepared-template top-1 search was **27.0x faster at p50**
  and **25.8x faster at p95** after adding the stronger edge-consistency reranker.
- At 1,000 templates, raw-capture-to-top-1 latency was **14.2x faster at p50**
  and **12.7x faster at p95**.
- Rust extraction alone was about **8.5x faster at p50**.
- Both matchers returned the expected user for all 100 exact probes at every
  measured index size.

SourceAFIS has important advantages that this speed test does not erase:

- published third-party FVC accuracy results;
- mature edge-graph minutiae matching and calibrated score semantics;
- a much smaller serialized template set;
- extensive algorithm-transparency tooling;
- a longer production and research history.

The custom SDK is the better architecture for fast 1:N clock-in at this location
size. SourceAFIS remains the stronger accuracy reference until the custom matcher
is evaluated on repeated impressions of the same physical fingers.

## Benchmark Setup

Measurements were taken on 2026-07-11:

| Item | Value |
| --- | --- |
| CPU | Intel Core 7 150U, 6 logical CPUs exposed |
| OS | Linux 6.6 under WSL2, x86_64 |
| Rust | 1.96.1, `--release` |
| Java | OpenJDK 21.0.11, warmed JVM |
| SourceAFIS | Java 3.18.1, 500 DPI raw-image option |
| Capture format | `400x500`, 8-bit grayscale |
| Canonical corpus | 1,269 unique captures, 1,097 users |
| Probe count | 100 per index size |
| Threads | Single-threaded identification loops |

The canonical corpus is `data/root/raw`. A separate 24-file convenience folder
contains duplicate filenames and was excluded. Earlier recursive SDK benchmark
runs reported 1,293 input paths because they included both directories.

Before measurement, both programs loaded capture bytes into memory, excluding
filesystem I/O. SourceAFIS extraction and matching were warmed before timed
runs. JVM startup and Rust process startup were excluded.

Candidates were the first `N` files in the same sorted path order. Every probe
was an exact capture already present among the candidates. This is suitable for
latency and top-1 sanity checks, but it is not a false-match/false-non-match test.

## Equivalent Operations

### Custom SDK

```text
raw bytes
  -> extract_raw_bytes
  -> BiometricIndex::search_users(top_k = 1)
  -> inverted-index retrieval
  -> geometric reranking of at most 8 candidates
  -> best record per user
```

### SourceAFIS

```text
raw bytes
  -> FingerprintTemplate(FingerprintImage)
  -> FingerprintMatcher(probe)
  -> matcher.match(candidate) for every candidate
  -> highest-scoring candidate
```

SourceAFIS's documented 1:N pattern is a complete application-side loop over
candidate templates. The benchmark did not stop when it reached the known exact
template. SourceAFIS `SEARCH` includes its required `FingerprintMatcher`
construction, because that probe preparation must happen once for every new
clock-in capture. Candidate `FingerprintTemplate` objects were already resident
in memory.

The custom SDK `SEARCH` starts from an extracted query template and uses its
location-level inverted index. `END_TO_END` re-extracts the raw probe before the
same search.

## Extraction

Measured over all 1,269 canonical captures:

| Engine | Average | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| Custom Rust SDK | 4.17 ms | 3.89 ms | 6.56 ms | 8.28 ms |
| SourceAFIS Java | 34.80 ms | 32.97 ms | 52.64 ms | 69.78 ms |

The custom extractor was 8.5x faster at p50 and 8.0x faster at p95. SourceAFIS
does substantially more image enhancement and skeleton cleanup, so this is a
speed/algorithm-depth comparison, not proof that the faster templates are as
robust.

## Prepared-Template 1:N Search

`SEARCH` excludes raw extraction. It includes all work required to produce the
best user from an already extracted probe.

| Templates | Custom SDK p50 | SourceAFIS p50 | Custom speedup |
| ---: | ---: | ---: | ---: |
| 1 | 0.17 ms | 4.14 ms | 24.0x |
| 10 | 1.70 ms | 2.86 ms | 1.7x |
| 100 | 1.95 ms | 9.77 ms | 5.0x |
| 500 | 2.07 ms | 39.05 ms | 18.9x |
| 1,000 | 2.48 ms | 66.86 ms | 27.0x |
| 1,269 | 2.39 ms | 84.31 ms | 35.3x |

The custom matcher is nearly flat after its shortlist fills because only token
postings and a bounded set of geometric candidates are processed. SourceAFIS
performs a highly optimized pairwise comparison, but total 1:N latency grows
with the number and complexity of candidate templates.

Tail latency at the intended location sizes was:

| Templates | Custom p95 | SourceAFIS p95 | Custom speedup |
| ---: | ---: | ---: | ---: |
| 1,000 | 3.45 ms | 88.86 ms | 25.8x |
| 1,269 | 3.42 ms | 115.26 ms | 33.7x |

The 10-template point is close because the custom verifier can already fill its
eight-record shortlist, while SourceAFIS has only ten pairwise comparisons to
perform. The global index becomes increasingly valuable as the location grows.

## Raw Capture To Top-1

`END_TO_END` includes template extraction and complete identification:

| Templates | Custom SDK p50 | SourceAFIS p50 | Custom speedup |
| ---: | ---: | ---: | ---: |
| 100 | 6.06 ms | 39.10 ms | 6.4x |
| 500 | 6.31 ms | 70.05 ms | 11.1x |
| 1,000 | 7.08 ms | 100.70 ms | 14.2x |
| 1,269 | 6.67 ms | 115.59 ms | 17.3x |

At 1,000 templates, p95 was 9.75 ms for the custom SDK and 123.57 ms for
SourceAFIS. Exact values will change on Android, but the scaling difference comes
from the algorithms: bounded candidate reranking versus a complete candidate
scan.

## Index Build And Probe Preparation

| Work | Custom SDK | SourceAFIS |
| --- | ---: | ---: |
| Build 1,000-template location index | 124-134 ms | No global index |
| Per-probe preparation at 1,000, p50 | Included in 2.48 ms search | 1.80 ms |
| Candidate scan at 1,000, p50 | Bounded inside search | 64.91 ms |

The custom index build is paid after local enrollment or a synchronization that
changes effective class templates. SourceAFIS avoids a location-level build,
but pays for every candidate comparison on each identification.

## Storage And Memory

The measured derived custom index is 3.59 MiB for 1,000 templates. The measured
SourceAFIS CBOR template set is 0.52 MiB, while its resident templates and probe
matcher used 12.52 MiB and 2.92 MiB respectively in the benchmark process.

These are different structures. The custom bytes are a candidate index with
student mapping and verifier features. The SourceAFIS bytes are serialized
biometric templates; application ownership, database rows, integrity metadata,
and synchronization remain outside that figure.

The SDK-owned libSQL architecture was introduced after this benchmark harness.
Current class database size, WAL transfer size, matcher-publication peak RSS,
and Android bootstrap cost have not yet been measured and are therefore omitted
instead of reusing numbers from a retired persistence design.

## Algorithm Comparison

| Area | Custom SDK | SourceAFIS |
| --- | --- | --- |
| Extraction resolution | Half-resolution minutiae skeleton | Full enhancement and minutiae pipeline |
| Candidate retrieval | Global pair/triplet token inverted index | Pairwise edge-hash lookup inside each comparison |
| Geometric model | Adaptive rigid-transform hypotheses | Edge-root search and minutiae pairing graph |
| Final verification | One-to-one pairing with supporting-edge consistency gate | Pairing expansion with detailed edge/minutia scoring |
| 1:N scaling | Posting visits plus bounded top candidates | Full candidate-template loop |
| Score | SDK-specific `0..1` blend | Shaped score with approximate FMR interpretation |
| Quality | Explicit custom `0..100` capture score | No equivalent simple public quality score |
| Transparency | Source and documented stages | Rich intermediate-data transparency API |
| Persistence | SDK-owned libSQL gallery, offline submissions, WAL sync | Template serialization; database belongs to app |
| Android | Rust native through UniFFI/KMP | Pure Java, API 24+ documented |

SourceAFIS uses a deeper matcher. It constructs invariant edges between minutiae,
finds compatible roots, expands a pairing graph, and scores minutia count/type,
supporting edges, distance error, and angle error. Its shaped score is calibrated
so increases roughly correspond to lower false-match rates.

The custom SDK deliberately spends less work. Quantized local descriptors
retrieve a shortlist; a small Hough-style vote evaluates one strong transform
and at most one fallback; one-to-one minutiae pairing is gated by a bounded
supporting-edge graph. That design is why it wins this 1:N benchmark, but it may
still be less tolerant of elastic skin deformation, partial overlap, pressure,
or difficult low-quality captures.

## Accuracy Evidence

The current corpus cannot compare genuine-match accuracy because `_1` and `_2`
are different thumbs, not repeated impressions of one finger. Exact self-probes
only verify that both systems can retrieve an enrolled template. Both scored
100/100 expected top-1 users at every measured size.

After the edge-consistency reranker change, a separate full-corpus regression
accepted 0/634 held-out different-finger probes at the default policy. The
maximum strongest-impostor score was 0.2485 against the 0.30 floor. Exact and
synthetically translated probes produced no accepted wrong users. These checks
protect score calibration, but they still do not measure genuine-match FNMR.

SourceAFIS has third-party FVC-onGoing results. Its published standard-dataset
result for version 3.14.0 reports 3.87% EER and 9.05% FNMR at FMR 0.01%. The
project itself describes this as the lower end of the accuracy spectrum and
notes difficulty with the lowest-quality fingerprints. It nevertheless has far
stronger accuracy evidence than this SDK currently has.

The custom SDK's translated-image and different-finger tests are useful
regressions, but they are not a substitute for independently captured same-finger
samples. No claim that the custom matcher is as accurate as SourceAFIS is
supported yet.

## Product-Level Differences

SourceAFIS is a matcher library. Its official 1:N tutorial leaves subject
mapping, candidate storage, search-loop orchestration, and persistence to the
application. Serialized templates are not guaranteed backward compatible, and
SourceAFIS recommends retaining original images for re-extraction on upgrade.

This SDK owns the class workflow: internal finger records, user collapsing,
quality policy, resumable group enrollment, duplicate-student checks, a
synchronized libSQL replica, immutable matcher publication, and Kotlin bindings.
It intentionally does not retain raw captures. These features should be
evaluated separately from matcher latency.

## Recommendation

Keep the custom indexed matcher for the target clock-in product. At 500 to 1,269
templates, the measured latency advantage is too large to ignore and aligns with
the offline Android requirement.

Before treating it as production-ready biometric evidence:

1. Collect at least three independently captured impressions of each enrolled
   thumb across placement, pressure, moisture, time, and representative devices.
2. Compare genuine and impostor score distributions for both engines on exactly
   the same identity split.
3. Measure rank-1 recall, FNMR at selected FMR, retry rate, and duplicate-user
   detection, not only exact top-1 speed.
4. Run both benchmarks on a low-end and typical Android device.
5. Measure current libSQL database and WAL cost before setting class sync limits
   or deciding whether template compaction is necessary.

A useful next accuracy experiment is a hybrid benchmark: use the custom inverted
index for shortlist retrieval and a stronger pairwise verifier on that shortlist.
That should be evaluated as an experiment rather than added to the SDK until
same-finger data shows whether the extra extraction, storage, and latency are
worth it.

## External References

- [SourceAFIS Java tutorial](https://sourceafis.machinezoo.com/java)
- [FingerprintMatcher API](https://sourceafis.machinezoo.com/javadoc/com.machinezoo.sourceafis/com/machinezoo/sourceafis/FingerprintMatcher.html)
- [FingerprintTemplate API](https://sourceafis.machinezoo.com/javadoc/com.machinezoo.sourceafis/com/machinezoo/sourceafis/FingerprintTemplate.html)
- [SourceAFIS algorithm overview](https://sourceafis.machinezoo.com/algorithm)
- [SourceAFIS benchmarks](https://sourceafis.machinezoo.com/benchmarks)
- [SourceAFIS algorithm transparency](https://sourceafis.machinezoo.com/transparency/)
