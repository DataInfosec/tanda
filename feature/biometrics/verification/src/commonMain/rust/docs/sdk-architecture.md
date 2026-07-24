# SDK Architecture

## Purpose

The biometric SDK is the device-side owner of fingerprint extraction, local
class-gallery persistence, libSQL synchronization, and offline 1:N matching.
The surrounding Android application passes provisioning and user actions. It
does not own a gallery schema or biometric persistence implementation.

The architecture is designed around five constraints:

1. Clock-in must continue without a network connection.
2. A device should search only the students assigned to its class gallery.
3. Enrollment must survive process death and delayed synchronization.
4. The server must enforce roster authority and school-wide uniqueness.
5. Matching must not block on SQL, synchronization, or index construction.

## Codebase Map

```mermaid
flowchart TB
    kotlin["kotlin.rs\nUniFFI application boundary"] --> campus["sdk/campus.rs\ngallery lifecycle and sync"]
    campus --> libsql["libSQL synced database\nclass-gallery.db"]
    campus --> gallery["sdk/gallery.rs\nimmutable matcher generation"]
    campus --> artifact["sdk/artifact.rs\nartifact validation"]
    campus --> enrollment["sdk/enrollment.rs\nenrollment policy and reports"]
    gallery --> index["sdk/index.rs\ncandidate index and verifier"]
    gallery --> extractor["sdk/extractor.rs\nquality and minutiae extraction"]
    artifact --> template["sdk/template.rs\nbounded template codec"]
    artifact --> index
    server["server_ffi.rs\nC ABI validator"] --> artifact
    index --> fingerprint["fingerprint.rs\nraw capture validation"]
    extractor --> fingerprint
```

| Module | Responsibility |
| --- | --- |
| `fingerprint.rs` | Validate and orient a `400x500` grayscale capture |
| `sdk/extractor.rs` | Build quality-scored minutiae and invariant descriptors |
| `sdk/index.rs` | Candidate retrieval, geometric verification, identity policy |
| `sdk/template.rs` | Encode and decode bounded extracted-template artifacts |
| `sdk/artifact.rs` | Verify metadata, checksum, ownership, and duplicates |
| `sdk/gallery.rs` | Pair validated template state with a derived search index |
| `sdk/enrollment.rs` | Define capture acceptance and rejection contracts |
| `sdk/campus.rs` | Own the replica, synchronization, batches, and publication |
| `kotlin.rs` | Expose the campus workflow to Android through UniFFI |
| `server_ffi.rs` | Reuse artifact validation from the Go server |

## Ownership Boundaries

```mermaid
flowchart LR
    app["Android application"] -->|"provisioning, captures, commands"| sdk["Rust SDK"]
    sdk -->|"libSQL protocol"| gateway["Tanda Campus gateway"]
    gateway --> postgres["PostgreSQL authority"]
    sdk --> replica["SDK-owned replica"]
    sdk --> matcher["SDK-owned matcher"]

    app -. "does not access" .-> replica
    gateway -. "does not decode" .-> capture["raw capture"]
    postgres -. "stores only extracted artifacts" .-> capture
```

The application owns UI state, sensor acquisition, provisioning delivery, and
clock-event creation. The SDK owns all biometric bytes after a capture is
submitted. Tanda Campus owns class membership, writer authority, canonical
template revisions, and enrollment decisions.

Raw captures are discarded after extraction. The durable payload is a
one-student `TemplateStore` containing extracted features only. Neither Go nor
Kotlin interprets that binary representation.

## Class Gallery Schema

The synchronized database uses schema identifier `tanda-class-gallery` and
schema version `2`.

| Table | Direction | Purpose |
| --- | --- | --- |
| `sync_metadata` | Server to device | Schema, stream, gallery, and domain revision |
| `roster_members` | Server to device | Effective class membership and profile revision |
| `gallery_templates` | Server to device | Canonical template per student and modality |
| `enrollment_batches` | Writer to server | Resumable group-enrollment lifecycle |
| `enrollment_submissions` | Writer to server | Durable candidate artifact and capture metadata |
| `enrollment_results` | Server to device | Immutable accepted or rejected decision |

The database deliberately stores current projection rows. Domain ordering is
represented by `gallery_revision`; physical replication ordering is represented
by libSQL WAL frames. These values are independent and must never be converted
into one another.

An enrollment submission records the gallery revision observed by the device.
This is evidence for audit and conflict analysis, not permission for the device
to assign a canonical revision.

## Initialization

`CampusBiometricSdk::open` performs the following sequence:

1. Validate device ID, endpoint, credential, extraction policy, and limits.
2. Create the SDK storage directory.
3. Acquire an exclusive file lease for the class replica.
4. Create a two-worker Tokio runtime owned by the SDK.
5. Open libSQL's synced database with remote SQL writes disabled.
6. Attempt an initial synchronization.
7. Validate schema metadata and every active template artifact.
8. Build an immutable `GalleryIndex`.
9. Publish the verified index and return the facade.

A new replica cannot be used until step 6 succeeds. An existing replica may
open in `Offline` state after a transient sync failure, but it still has to pass
local schema and artifact validation. This prevents an empty or corrupt first
install from appearing usable.

Only one SDK process may own a replica directory. The file lease fails fast
instead of allowing two local libSQL clients to write the same file.

## Synchronization Algorithm

Synchronization is explicit. The SDK does not run a hidden network loop.

```mermaid
sequenceDiagram
    participant App
    participant SDK
    participant Replica as class-gallery.db
    participant Gateway
    participant Matcher as ArcSwap matcher

    App->>SDK: sync()
    SDK->>SDK: acquire operation mutex
    SDK->>Gateway: libSQL push and pull
    Gateway-->>Replica: merged committed WAL
    SDK->>Replica: read metadata, roster, templates, pending rows
    SDK->>SDK: validate all artifacts and build replacement index
    alt replacement is valid
        SDK->>Matcher: atomic store(new index)
        SDK-->>App: Ready report
    else row or artifact is invalid
        SDK->>SDK: mark Quarantined
        SDK-->>App: integrity error
        Note over SDK,Matcher: previous verified index remains available
    end
```

The operation mutex serializes `sync`, credential rotation, enrollment writes,
and group-batch changes. This gives each operation a stable database view and
prevents a sync from racing a local transaction. Identification does not use
that mutex.

The rebuilt matcher is staged completely before publication. Validation or
allocation failure cannot partially mutate the live generation.

## Offline Identification

`identify_raw_bytes` loads the current `Arc<GalleryIndex>` from `ArcSwap`, then
extracts and searches without touching the database. Existing identify calls
retain their `Arc` while a newly synchronized matcher is published.

```mermaid
flowchart LR
    scan["Raw scan"] --> extract["Extract query template"]
    extract --> retrieve["Descriptor candidate retrieval"]
    retrieve --> verify["Geometric verification"]
    verify --> collapse["Best finger per student"]
    collapse --> decision{"Acceptance and margin policy"}
    decision -->|"passes"| match["Match(student_id)"]
    decision -->|"fails"| retry["Retry(reason)"]
```

The matcher never selects a student solely because one candidate ranked first.
Low quality, no shared descriptors, weak verification, or an ambiguous top-two
margin produces `Retry`.

## Enrollment Algorithm

Single and group enrollment use the same durable submission path. A group batch
adds operator lifecycle metadata; it does not change artifact semantics.

For one student, the SDK:

1. Requires current active roster membership in the local projection.
2. Extracts every supplied capture and assigns a UUIDv7 finger-record ID.
3. Rejects invalid, low-quality, or excess captures individually.
4. Searches accepted captures against other students in the current class.
5. Rolls back all accepted captures if any cross-student duplicate is found.
6. Encodes accepted captures as one bounded one-student artifact.
7. Computes the canonical `sha256:<hex>` payload checksum.
8. Commits a UUIDv7 submission row in one immediate SQLite transaction.
9. Rebuilds the local matcher so the writer can identify the student offline.

The local duplicate check is a fast operator safeguard. It is not the final
authority because the same finger can be enrolled concurrently in another
class while both writers are offline. The server repeats duplicate matching
against current canonical templates for the entire school while holding a
school-scoped serialization lock.

### Provisional State

Pending rows are included only when `submission.device_id` matches this SDK's
provisioned device ID. Reader devices therefore never treat another device's
unreviewed submission as canonical.

After synchronization:

- an accepted result is accompanied by the canonical template revision;
- a rejected result removes the provisional candidate from the writer matcher;
- an undecided row remains provisionally searchable on its originating writer.

## Group Enrollment

At most one `active` batch exists per device ID. Its owner, ID, and timestamps
are stored in libSQL, so Android can resume the workflow after process death.
Closing or cancelling a batch does not delete submissions that were already
committed.

An unfinished old-writer batch remains available for audit but does not block a
replacement writer from starting its own batch. Different class galleries also
use independent replicas and may run group enrollment simultaneously.

## Writer Switching

Writer authority is enforced by the sync gateway, not by a mutable app flag.
When an administrator assigns a replacement writer:

1. The server closes the old writer assignment and increments its epoch.
2. The old credential can no longer push class-gallery WAL.
3. The replacement device receives a credential for the current epoch.
4. The replacement bootstraps or refreshes the canonical class replica.
5. Enrollment resumes after the replacement reaches `Ready`.

A gateway writer-forbidden response moves the SDK to `WriterRevoked`. Existing
matching remains available, while enrollment and further mutation are blocked.
The application should surface pending enrollment count before retiring the old
device because unsynchronized local rows cannot be reconstructed elsewhere.

## Credential Rotation

`rotate_auth_token` closes the current libSQL handle, opens a candidate handle
with the new token, and verifies it with an immediate sync. Failure restores the
previous configuration and keeps the last matcher. A successful sync validates
rows and publishes a matcher before the new token becomes active in SDK state.

The sync URL and device identity are immutable for one replica directory. A
different assignment should use a separately provisioned storage directory.

## Failure States

| State | Matching | Enrollment | Recovery |
| --- | --- | --- | --- |
| `Ready` | Available | Available to active writer | Normal operation |
| `Offline` | Last verified matcher | Durable local enrollment allowed | Retry explicit sync |
| `WriterRevoked` | Last verified matcher | Blocked | Resolve or replace writer assignment |
| `Quarantined` | Last verified matcher | Blocked | Repair server projection and sync valid state |

Database and sync errors are separate stable categories. Kotlin callers branch
on generated exception variants rather than parsing diagnostic strings.

## Artifact Format

`TemplateStore` starts with the eight-byte `BMSTPL\0\0` magic and a little-endian
record count. Each record contains bounded UTF-8 identifiers, quality, selected
descriptor tokens, and verifier features. Decoding validates lengths before
allocation, rejects trailing bytes, and validates every extracted template.

The SQL row carries format version, extractor profile, and SHA-256 checksum
outside the payload. `decode_student_template_artifact` verifies all three plus
single-student ownership before the record can enter an index.

The format does not contain:

- raw fingerprint pixels;
- a class or school identifier;
- roster membership;
- a WAL position or remote endpoint;
- a derived candidate index.

This separation lets the same canonical student artifact be projected into a
new class after an administrative move without re-enrollment.

## Server Reuse

The `server-ffi` feature compiles the artifact decoder and duplicate policy into
`libbiometric_sdk.so` without libSQL or UniFFI. Its C ABI catches Rust panics,
borrows all input memory for one call, and returns expected rejection outcomes
as stable integer codes. Go never deserializes a template.

Dynamic linking is intentional. The Go libSQL driver already embeds Rust
objects, and combining both Rust static archives can define the same runtime
symbols. A separately packaged shared library avoids that collision and gives
the server an explicit versioned runtime dependency.

## Test Strategy

The repository tests:

- bounded template encode/decode and corruption rejection;
- extraction, candidate retrieval, geometric verification, and retry policy;
- artifact checksum and ownership validation;
- local roster authorization and durable provisional enrollment;
- per-writer group batch singleton, restart recovery, and writer handoff;
- quarantine behavior with preservation of the previous matcher;
- Kotlin error and state conversion;
- panic-contained C ABI classification;
- real SDK enrollment through the Go libSQL gateway in the Tanda Campus suite.

Performance and biometric accuracy require separate evidence. Unit and
integration tests establish protocol and state-machine behavior, while the
corpus benchmarks in [performance.md](performance.md) measure matcher cost and
known accuracy limitations.
