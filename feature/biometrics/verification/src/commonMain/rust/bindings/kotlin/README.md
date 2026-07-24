# Kotlin And Android Integration

`MobileBiometricSdk` gives the Android application one API for clock-in,
enrollment, and fixed-population gallery synchronization. Rust owns the biometric data and
local database; the application owns screens, device provisioning, network
scheduling, and attendance events.

The most important design decision is that matching is local. A network outage
must not stop an already provisioned device from identifying subjects.

```text
                         explicit sync
  Tanda Campus server  ◄────────────────►  MobileBiometricSdk
         │                                      │
         │ canonical membership and templates   ├── private gallery.db
         │ enrollment decisions                 └── in-memory matcher
         │
         └── remains the source of truth

  Fingerprint scanner ──► identify(raw capture) ──► Match or Retry
                                      │
                                      └── no network request
```

## Key Terms

A **gallery** is the biometric matching dataset for one fixed population. It
contains active subject membership and accepted fingerprint templates. Each
physical device instance keeps a private local replica and builds its
in-memory matcher from it; a writer may temporarily add its own provisional
enrollment while awaiting a server decision.

The **gallery revision** is the server-assigned application revision of the
canonical membership and templates represented by the current matcher. It lets the
app and operations tooling tell how current a device is. It is not a database
frame count or a value Android should create or increment.

## The Two Device Modes

Every SDK instance is provisioned for one gallery. Devices using
different galleries must use different storage roots.

### Why separate readers and writers?

A population may need many attendance devices, but it only needs
one place creating new biometric records. Separating the roles gives us:

- **Consistent enrollment:** one writer prevents two offline devices from
  independently enrolling conflicting records for the same gallery.
- **Clear ownership:** operators know which device contains unsynchronized
  enrollment and must be recovered before replacement.
- **Least privilege:** ordinary attendance devices only need permission to pull
  accepted data; they cannot submit biometric changes.
- **Safe scaling:** adding more clock-in devices does not create more biometric
  writers or change the canonical gallery.
- **Simple recovery:** the server can revoke one writer and appoint a
  replacement without interrupting identification on readers.

The server still performs the final site-wide duplicate check and enrollment
decision. The single-writer rule reduces avoidable conflicts; it does not make
the writer the source of truth.

### Attendance reader

An attendance reader:

- downloads the gallery membership and accepted fingerprint templates;
- identifies subjects locally for clock-in;
- periodically pulls gallery changes;
- never enrolls subjects.

Use this mode for ordinary attendance devices. Several readers can use the same
gallery because each device has its own local replica.

### Enrollment writer

An enrollment writer can do everything a reader does, plus:

- enroll one online-authorized subject;
- run a resumable group-enrollment session;
- retain an authorized submission if connectivity drops after capture begins;
- push those submissions for server review.

Only one device at a time is assigned as the enrollment writer for a gallery. The
server controls that assignment. The application must only show enrollment UI
on the assigned writer.

Reader/writer mode is a provisioning and product decision, not a switch passed
to `MobileBiometricSdk.open()`. The physical device-instance ID and credential
identify the instance to the server.

## What A Mobile Developer Needs To Own

The Android application is responsible for:

- showing clock-in UI on readers and enrollment UI only on the assigned writer;
- integrating the scanner vendor's SDK and obtaining raw fingerprint images;
- obtaining and securely storing provisioning values;
- opening and closing the SDK for the current gallery assignment;
- scheduling sync based on connectivity, lifecycle, and pending enrollment;
- keeping blocking SDK calls off the main thread;
- turning `Match`, `Retry`, sync states, and typed errors into product behavior;
- recording attendance events after a successful match.

The application does **not** need to understand or manage:

- the database schema, transactions, or synchronization frames;
- fingerprint-template encoding, checksums, or matcher indexes;
- how synchronized rows are validated;
- how a replacement matcher is built or swapped into service;
- how the server stores canonical biometric artifacts.

Those are SDK/server responsibilities. The mobile integration should treat the
database and template bytes as private, opaque implementation details.

## Network And Safety States

Reader/writer mode describes what the device is allowed to do. The sync state
describes whether its local data is currently safe to use.

| State | Identification | Enrollment on assigned writer | Application action |
| --- | --- | --- | --- |
| `Ready` | Available | Available | Normal operation |
| `Offline` | Last verified matcher remains available | Cannot initiate without a fresh online authorization | Continue clocking and schedule another sync |
| `WriterRevoked` | Last verified matcher remains available | Blocked | Hide enrollment and refresh the writer assignment |
| `Quarantined` | Last verified matcher remains available | Blocked | Raise an integrity alert and repair/re-provision |

`Offline` is expected during temporary network failure. It is not data
corruption. `Quarantined` means newly synchronized data failed validation, so
the SDK rejected it and kept the previous verified matcher.

## Startup And Lifetime

The server provisions:

- a stable physical device-instance ID;
- the sync URL for its assigned gallery;
- a long-lived, revocable bearer credential.

Open one SDK instance and keep it for as long as the device remains assigned to
that gallery:

```kotlin
val sdk = MobileBiometricSdk.open(
    storageRoot = context.filesDir.resolve("biometric/gallery").path,
    deviceInstanceId = provisioning.deviceInstanceId,
    syncUrl = provisioning.gallerySyncUrl,
    authToken = provisioning.authToken,
    enrollmentMinQuality = null, // Rust default: 65
)
```

The first open of a new storage root requires connectivity because there is no
local gallery yet. Later opens can use the last verified gallery when the
server is temporarily unavailable.

The SDK creates:

```text
storageRoot/
  gallery.db
  gallery.lock
```

Keep these rules:

- Use a private application directory.
- Do not open, copy, checkpoint, or edit the database from Kotlin.
- Do not use the same storage root for two live SDK instances.
- Do not reuse a storage root for another physical device instance.
- Close the generated `AutoCloseable` object when the assignment ends.
- Use a new storage root when changing to another gallery.
- Call `open`, `sync`, enrollment, and token rotation off the main thread.
- Treat `identify` as CPU work and also keep it off the main thread.

## How Synchronization Works

The SDK never starts background jobs. Android decides when to call `sync()`,
normally after provisioning, when connectivity returns, after enrollment, and
periodically according to application policy.

One successful sync performs the whole update:

```text
  local pending enrollment
             │
             ▼
  1. push local changes ───────────────► server
                                             │
  2. pull membership, templates, decisions ◄─┘
             │
  3. validate every synchronized artifact
             │
  4. build a replacement matcher
             │
  5. publish the matcher atomically
             ▼
           Ready
```

The matcher is replaced only after all new data passes validation. If network
transfer fails, the state becomes `Offline`. If downloaded data fails an
integrity check, the state becomes `Quarantined`. In both cases, identification
continues with the previous verified matcher.

```kotlin
try {
    val report = sdk.sync()
    publishGalleryHealth(report)
} catch (error: MobileSdkException) {
    when (sdk.syncState()) {
        MobileSyncState.Offline -> scheduleRetry()
        MobileSyncState.WriterRevoked -> stopEnrollment()
        MobileSyncState.Quarantined -> raiseIntegrityAlert()
        MobileSyncState.Ready -> recordTransientFailure(error)
    }
}
```

A thrown sync error does not mean the local matcher disappeared. Read
`syncState()` before deciding whether clock-in should stop.

### Enrollment reconciliation

An accepted local enrollment is immediately durable and provisionally
searchable on the writer that captured it:

```text
  writer begins an online-authorized enrollment
          │
          ├── durable local submission
          └── provisional local match
                       │
                    next sync
                       ▼
              server duplicate checks
                 ┌─────┴─────┐
              accepted     rejected
                 │             │
          canonical entry   provisional entry
          replaces local    is removed
```

The server remains authoritative for gallery membership, writer assignment,
site-wide duplicate detection, and final enrollment acceptance. Reader
devices only receive canonical accepted templates.

Use `pendingEnrollmentCount()` before retiring or replacing a writer. A
non-zero count means submissions have not received a server decision and may be
stranded if the device is discarded.

## Clock-In

The biometric SDK does not communicate with fingerprint hardware. Android must
use the scanner vendor's SDK to capture a raw `400x500`, 8-bit grayscale image
and pass its bytes to `identify()` or `enrollSubject()`. Device discovery,
permissions, capture prompts, and scanner errors remain in the vendor/mobile
integration.

Identification reads the current in-memory gallery matcher. It performs no SQL or
network operation:

```kotlin
when (val outcome = sdk.identify(rawCapture)) {
    is MobileIdentifyOutcome.Match -> recordClockEvent(
        subjectId = outcome.subjectId,
        recordId = outcome.recordId,
        galleryId = outcome.galleryId,
        galleryRevision = outcome.galleryRevision,
        modality = outcome.modality,
        score = outcome.score,
        verificationScore = outcome.verificationScore,
    )
    is MobileIdentifyOutcome.Retry -> requestAnotherScan(outcome.reason)
}
```

`Retry` means the app should acquire another fingerprint. Its reason tells the
UI whether quality was low, no candidate was found, the score was weak, or the
best two subjects were ambiguous. Never turn the best diagnostic candidate
inside a retry into a successful clock-in.

The matcher only knows the assigned population. Attendance location and the final
clock event belong to the application/server, not this SDK.

## Single Enrollment

Subject creation and administrator authentication remain application concerns.
The subject must be an active member of the synchronized gallery. Check local
readiness, then obtain a short-lived, subject-specific authorization online
before requesting fingerprint capture:

```kotlin
when (sdk.enrollmentReadiness(subjectId)) {
    MobileEnrollmentReadiness.GALLERY_SYNC_REQUIRED -> sdk.sync()
    MobileEnrollmentReadiness.WRITER_REVOKED -> stopEnrollment()
    MobileEnrollmentReadiness.QUARANTINED -> raiseIntegrityAlert()
    MobileEnrollmentReadiness.READY -> Unit
}

val authorization = campusApi.authorizeSubjectEnrollment(subjectId)
val result = sdk.enrollSubject(
    authorization = MobileSubjectEnrollmentAuthorization(
        enrollmentOperationId = authorization.operationId,
        performedBy = authorization.performedBy,
        deviceInstanceId = authorization.deviceInstanceId,
        galleryId = authorization.galleryId,
        subjectId = authorization.subjectId,
        batchId = null,
        authorizationExpiresAt = authorization.expiresAt,
    ),
    captures = listOf(leftThumb, rightThumb),
)

if (result.submissionId != null) {
    enqueueBiometricSync()
}
```

The report contains one result per capture. Low-quality captures can be
rejected individually. A match against another enrolled subject rejects the
candidate submission. Raw captures are processed during the call and are not
stored by the SDK.

## Group Enrollment

A group batch exists for the real operator workflow of enrolling a population over
many scans, interruptions, or app restarts. It records one resumable session;
each subject's accepted submission still commits independently, so the batch is
not an all-or-nothing transaction:

```kotlin
val batchGrant = campusApi.authorizeEnrollmentBatch()
val batch = sdk.activeGroupEnrollment() ?: sdk.startGroupEnrollment(
    MobileEnrollmentBatchAuthorization(
        authorizationId = batchGrant.authorizationId,
        performedBy = batchGrant.performedBy,
        deviceInstanceId = batchGrant.deviceInstanceId,
        galleryId = batchGrant.galleryId,
        authorizationExpiresAt = batchGrant.expiresAt,
    )
)

for (subject in selectedSubjects) {
    val grant = campusApi.authorizeSubjectEnrollment(subject.id, batch.id)
    val result = sdk.enrollSubject(
        authorization = MobileSubjectEnrollmentAuthorization(
            enrollmentOperationId = grant.operationId,
            performedBy = grant.performedBy,
            deviceInstanceId = grant.deviceInstanceId,
            galleryId = grant.galleryId,
            subjectId = grant.subjectId,
            batchId = batch.id,
            authorizationExpiresAt = grant.expiresAt,
        ),
        captures = acquireCaptures(subject),
    )
    showEnrollmentResult(subject, result)
}

sdk.finishGroupEnrollment(batch.id)
```

Only one batch can be active for the writer. Use
`cancelGroupEnrollment(batch.id)` when the operator abandons it. Finishing or
cancelling a batch does not delete submissions already committed locally.

## Writer Replacement

When the server assigns a replacement writer:

1. The old writer keeps identification available.
2. Its next rejected push changes its state to `WriterRevoked`.
3. Further enrollment on the old writer is blocked.
4. The replacement writer opens with its own device-instance ID and credential.
5. The replacement bootstraps the canonical gallery before enrollment.

Do not copy the old writer's database to the new device. Check the old device's
pending count before retiring it whenever possible.

## Credential Rotation

```kotlin
val report = sdk.rotateAuthToken(newCredential.token)
```

Rotation verifies the new token with an immediate sync. If verification fails,
the SDK restores the previous in-memory credential and keeps the existing
matcher. Store credentials using Keystore-backed application storage and never
log them.

## Errors And Sensitive Data

Branch on stable error categories; use messages only for diagnostics:

| Category | Typical application action |
| --- | --- |
| `InvalidInput` | Reject caller data or provisioning |
| `Conflict` | Refresh assignment, authorization, or membership state |
| `SessionActive` | Close the other instance or resume the existing batch |
| `Integrity` | Block enrollment and raise an operational alert |
| `Database` | Preserve evidence and retry or re-provision under support policy |
| `Sync` | Continue offline and schedule a retry |
| `SchemaUnsupported` | Require an SDK/application upgrade |
| `ResourceLimit` | Reject oversized or implausible input |

Never log raw captures, template payloads, auth tokens, or identity-linked match
scores. Treat subject IDs and submission IDs according to the application's PII
policy.

## Build And Package

The mobile build needs generated Kotlin plus one native library for each
supported Android ABI. Generated Kotlin is not committed to this repository.

```bash
make uniffi-lib              # host library used to generate Kotlin
make verify-uniffi-bindings  # generation smoke test
make android-libs            # Android ABI libraries
```

Generate Kotlin from `target/release/libbiometric_sdk.so` with the mobile
team's UniFFI `0.32.0` toolchain. Run generation from the repository root so
the package configuration is discovered.

Package the outputs like this:

```text
commonMain/
  BiometricClock.kt

androidMain/
  AndroidBiometricClock.kt
  kotlin/com/datainfosec/biometric/biometric_sdk.kt  # generated
  jniLibs/arm64-v8a/libbiometric_sdk.so
  jniLibs/armeabi-v7a/libbiometric_sdk.so
  jniLibs/x86_64/libbiometric_sdk.so
```

The generated Kotlin requires JNA `5.12.0` or newer and AndroidX annotations.
Pin both dependencies, keep generated code in `androidMain`, and use an Android
build pipeline that preserves 16 KiB page-size compatibility. The generated
code uses JVM/Android APIs and cannot compile in `commonMain`.
