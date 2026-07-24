# Biometric SDK

Pure Rust fingerprint enrollment and offline 1:N identification for Tanda
Campus devices. The SDK owns a synchronized libSQL class gallery, exposes a
small Kotlin API through UniFFI, and provides the same artifact validator to the
Go server through a panic-contained C ABI.

Raw fingerprint images are used only during extraction. They are not stored in
libSQL, PostgreSQL, template artifacts, or the in-memory matcher. Extracted
templates remain sensitive biometric data and require encrypted device storage,
TLS, scoped credentials, and restricted server access.

## Campus Model

One class gallery represents one `(school, academic session, class section)`.
Every device assigned to that class receives the same roster and canonical
templates. One switchable enrollment writer may also create local enrollment
submissions; reader devices only pull.

```mermaid
flowchart LR
    admin["Admin roster changes"] --> server["Tanda Campus and PostgreSQL"]
    writer["Class enrollment device"] <-->|"libSQL WAL sync"| server
    readers["Class attendance devices"] <-->|"libSQL WAL pull"| server
    writer --> local["SDK-owned class-gallery.db"]
    readers --> localReaders["SDK-owned class-gallery.db"]
    local --> matcher["Immutable class matcher"]
    localReaders --> matcherReaders["Immutable class matcher"]
```

The Android application supplies only provisioning and commands. It does not
open SQLite, execute SQL, move WAL frames, decode template payloads, or manage
gallery files.

## Responsibilities

The SDK:

- validates `400x500` grayscale fingerprint captures;
- extracts quality-scored minutiae and invariant descriptors;
- persists one SDK-owned `class-gallery.db` replica;
- pushes and pulls through the official libSQL sync client;
- keeps enrollment submissions durable while offline;
- enforces one active, resumable group-enrollment batch per writer;
- performs local duplicate checks against the current class;
- rebuilds and atomically publishes an immutable 1:N matcher;
- returns `Match(student_id)` or a stable `Retry` reason;
- validates opaque one-student artifacts on the Go server.

Tanda Campus remains authoritative for class membership, writer assignment,
school-wide duplicate checks, canonical template revisions, and enrollment
decisions.

## Device Flow

```mermaid
sequenceDiagram
    participant App as Android app
    participant SDK as Biometric SDK
    participant DB as class-gallery.db
    participant Gateway as Tanda sync gateway

    App->>SDK: open(storage root, device id, sync URL, token)
    SDK->>Gateway: libSQL bootstrap or refresh
    Gateway-->>SDK: class roster and templates
    SDK->>DB: validate synchronized rows
    SDK->>SDK: build and publish matcher
    App->>SDK: enrollStudent(student id, captures)
    SDK->>SDK: extract and run class duplicate policy
    SDK->>DB: commit enrollment submission
    Note over App,DB: The submission survives loss of network or process restart
    App->>SDK: sync()
    SDK->>Gateway: push local WAL and pull server decisions
    SDK->>SDK: validate and publish replacement matcher
```

Identification never waits for network or database I/O. It reads the latest
published matcher through `ArcSwap`; a failed sync or invalid replacement leaves
the previous verified matcher available.

## Rust API

Enable `campus-libsql` when embedding the campus facade without Kotlin:

```rust
use biometric_sdk::sdk::{
    CampusBiometricSdk, CampusConfig, CampusProvisioning, IdentifyResult,
};

let provisioning = CampusProvisioning::new(
    "device-42",
    "https://campus.example/v1/libsql/class-gallery",
    device_token,
);
let sdk = CampusBiometricSdk::open(CampusConfig::new(
    "/app/files/biometric",
    provisioning,
))?;

match sdk.identify_raw_bytes(&capture)? {
    IdentifyResult::Match(hit) => record_clock_event(&hit.user_id),
    IdentifyResult::Retry(retry) => request_another_scan(retry.reason),
}
```

The first synchronized open requires connectivity. Later opens may use an
existing verified replica in `Offline` state. Network access occurs only during
`open`, `sync`, and `rotate_auth_token`.

Enrollment is a local transaction and therefore works offline:

```rust
let batch = sdk.start_enrollment_batch()?;
let result = sdk.enroll_student(
    "STUDENT-001",
    [&left_thumb[..], &right_thumb[..]],
    Some(&batch.id),
)?;
sdk.close_enrollment_batch(&batch.id)?;

if result.submission_id.is_some() {
    schedule_sync();
}
```

Submission and batch identifiers are UUIDv7 values generated inside the SDK.
Pending captures are provisionally searchable only on the writer that created
them. A later server decision either replaces the provisional template with its
canonical revision or removes it.

## Matcher

The identification path has two stages:

```text
raw capture
  -> quality and minutiae extraction
  -> invariant descriptor candidate lookup
  -> geometric minutiae verification
  -> best template per student
  -> Match or Retry
```

Default clock-in policy:

| Check | Default |
| --- | ---: |
| Query quality | `40` |
| Blended score | `0.30` |
| Geometric verification | `0.20` |
| Best-vs-runner-up margin | `0.02` |
| Enrollment quality | `65` |

See [matcher algorithm](docs/matcher-algorithm.md),
[performance](docs/performance.md), and
[SourceAFIS comparison](docs/sourceafis-comparison.md) for implementation and
measurement details. The current corpus is an engineering benchmark, not a
production FAR/FRR certification.

## Artifact Contract

The synchronized tables and PostgreSQL store carry one opaque template artifact
per student and modality:

| Field | Meaning |
| --- | --- |
| `student_id` | Expected owner of every encoded record |
| `sdk_format_version` | Binary format contract |
| `extractor_profile` | Extraction and matching profile |
| `template_payload` | Bounded SDK-owned bytes |
| `payload_sha256` | `sha256:<lowercase hex>` integrity digest |

The artifact contains extracted templates only. It has no roster, gallery,
database, synchronization cursor, raw capture, or derived search index. Go and
Kotlin treat the payload as opaque.

## Kotlin And Android

The `uniffi-bindings` feature embeds a `MobileBiometricSdk` API in the native
library using UniFFI proc-macro metadata. It exposes provisioning, explicit
sync, offline identification, single enrollment, group enrollment, pending
counts, and credential rotation. The mobile team's UniFFI toolchain generates
Kotlin from that metadata; generated Kotlin is intentionally not checked into
this repository.

Run `make uniffi-lib` for a host library suitable for binding generation and
`make android-libs` for the ABI-specific Android libraries. The generation
contract, lifecycle, and Android integration details are in the
[Kotlin integration guide](bindings/kotlin/README.md).

## Server Validator

The `server-ffi` feature builds a small shared library without libSQL or UniFFI:

```bash
make server-lib
make install-server-lib DESTDIR=/tmp/biometric-sdk-package PREFIX=/usr/local
```

The package contains `libbiometric_sdk.so`, `biometric_sdk.h`, and
`biometric-sdk.pc`. Tanda Campus links it with the Go build tag
`biometric_rust`. See [server FFI](docs/server-ffi.md).

## Development

Rust `1.88` is the minimum supported version.

```bash
cargo fmt --all --check
cargo test --locked
cargo test --locked --no-default-features --features server-ffi
cargo clippy --locked --all-targets --all-features -- -D warnings
RUSTDOCFLAGS="-D warnings" cargo doc --locked --no-deps --document-private-items --all-features
```

Architecture, schema, synchronization states, and failure handling are covered
in [SDK architecture](docs/sdk-architecture.md) and
[campus libSQL integration](docs/campus-libsql.md).
