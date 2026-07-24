# Tanda Attendance Support Plan

## Purpose

Update the biometric SDK to support the TandaSecure time-and-attendance product
for students, employees, and other attendance subjects without turning the SDK
into an attendance application.

The SDK will continue to own fingerprint extraction, enrollment artifacts,
offline matching, its synchronized local gallery, and server-side artifact
validation. Tanda Campus will own people, administrator permissions, attendance
sessions, attendance points, schedules, clock events, exeats, reports, and
notifications.

## Pre-deployment hard transition

This is a direct pre-deployment cutover. Rename the current SDK surface and
storage contract in place, delete legacy Campus/student naming and replica
detection, and update all repository consumers atomically. Do not add adapter
modules, compatibility aliases, parallel entry points, dual-schema reads, or
v2 APIs. Existing development replicas must be deleted and bootstrapped with
the canonical attendance gallery schema.

## Agreed Device Model

- One logical device ID is provisioned for one fixed biometric gallery
  population and attendance point.
- The same logical device ID may provision several physical device instances at
  that point.
- Each physical instance owns one SDK instance, gallery replica, storage root,
  physical instance ID, long-lived instance credential, and synchronization
  state.
- A gallery may have many attendance-reader instances but at most one active
  enrollment-writer instance. Writer assignment, replacement, and revocation
  remain server-owned and require an online Admin operation.
- A student class gallery may be provisioned to devices located in classrooms,
  hostels, dining halls, assembly areas, or gates.
- The device assignment in Tanda Campus determines the attendance point and
  activity. The biometric gallery determines only who the device may identify.
- Employee attendance uses separate employee-population devices and galleries.
- A physical device instance does not open, combine, or search multiple
  galleries.
- Canonical fingerprint artifacts may be projected into multiple devices using
  the same population gallery without biometric re-enrollment.

## Scope

### SDK responsibilities

- Maintain one synchronized gallery for a fixed population.
- Enroll an online-authorized gallery subject and identify subjects by
  fingerprint.
- Operate from the last verified matcher while offline.
- Return sufficient evidence for an auditable attendance event.
- Durably retain an authorized enrollment submission if connectivity is lost
  after capture.
- Return a stable provisioning/synchronization outcome when an authorized
  subject has not yet reached the local gallery.
- Reject malformed artifacts and cross-subject biometric duplicates.
- Expose stable Kotlin and server-FFI contracts.

### Out of scope

- Attendance schedules and sessions.
- Present, absent, late, early-departure, pending, or overtime calculation.
- Exeat permission or movement rules.
- RFID and QR verification.
- Attendance-event storage and synchronization.
- Administrator authorization.
- Subject creation, partial-profile completion, form registration, and bulk
  profile import.
- Student or employee profile data.
- Attendance reports and notifications.
- Multi-gallery search or gallery lifecycle management.

## Target Domain Contract

The biometric core already uses `user_id` internally. The synchronized and
public attendance contracts will adopt attendance-neutral subject terminology:

| Current term | Target term |
| --- | --- |
| `student_id` | `subject_id` |
| `roster_members` | `gallery_members` |
| `enrollment_id` | `membership_id` |
| `enroll_student` | `enroll_subject` |
| student count | subject count |
| student duplicate | subject duplicate |

`subject_id` must be the canonical Tanda Campus identity and must not be derived
from a student number, employee number, or other human-entered person reference.
Tanda Campus remains authoritative for the subject type, external references,
profile completeness, and attendance eligibility.

Gallery metadata will identify the fixed population:

- `gallery_id`: opaque server-authoritative gallery identity;
- `gallery_revision`: canonical population/template revision;
- `schema` and `schema_version`: synchronized layout contract.

Population type and attendance behavior remain server-owned. The SDK treats the
gallery and its subject membership as opaque provisioning data. A minimally
registered subject and a profile-complete subject are equivalent to the SDK
once either has an active gallery membership.

## Required Changes

### 1. Synchronized gallery schema

Update the current pre-release schema in place:

- rename `roster_members` to `gallery_members`;
- rename subject-bearing columns from `student_id` to `subject_id`;
- rename `enrollment_id` to `membership_id`;
- retain effective membership, template revision, enrollment batches,
  enrollment submissions, and enrollment results;
- retain one active enrollment batch per writer device;
- retain `fingerprint` as the implemented modality.

There is no deployed replica to migrate. Development replicas will be deleted
and freshly bootstrapped after the coordinated server and SDK change.

The schema definition in this repository and the bootstrap schema in Tanda
Tanda Campus must remain contract-compatible in table, column, constraint, and
metadata semantics.

### 2. Rust attendance facade

- Generalize roster checks and enrollment from students to subjects.
- Rename student-specific public types, fields, diagnostics, and documentation.
- Preserve the existing one-gallery lifecycle, file lease, explicit sync,
  writer revocation, quarantine, and atomic matcher publication.
- Preserve the underlying template format unless testing reveals a real need to
  rename its internal `user_id`; the current value is already semantically
  generic.

### 3. Atomic identification evidence

A successful identification result must include:

- `subject_id`;
- `record_id`;
- `gallery_id`;
- `gallery_revision`;
- `modality`;
- `score`;
- `verification_score`.

Gallery identity and revision must be captured from the same immutable matcher
used for identification. The application must not need to call a separate
gallery-summary method to assemble attendance evidence.

A retry remains a typed non-match with its stable reason and optional diagnostic
scores. Diagnostic candidates must never be treated as successful matches.

### 4. Enrollment attribution

Enrollment cannot be initiated without an online Tanda Campus administrator
session. Tanda Campus first authorizes the administrator and active writer
instance and returns a bounded, opaque enrollment authorization.

A single-subject authorization is server-bound to:

- operation ID;
- canonical administrator;
- physical writer instance and gallery;
- canonical subject;
- issue and expiry time;
- idempotency/use state.

An online-created group batch is bound to the administrator, physical writer
instance, and gallery, but it is an organizer rather than a blanket capture
grant. Every `enroll_subject` call receives its own single-subject
authorization bound to the batch. The subject must still be an active gallery
member, and a resumable local batch does not become an offline administrator
grant.

The SDK accepts the server-issued authorization context rather than unrelated,
freely supplied operation and administrator fields. It preserves the opaque
operation ID and server-supplied attribution, while Tanda Campus remains
responsible for canonical validation. An authorization cannot be reused for
another writer, gallery, subject, or batch.

Persist at least:

- `performed_by` on the enrollment batch;
- `performed_by` on each submission, so single enrollment is also attributable;
- server-issued enrollment operation ID;
- logical device ID and physical device-instance ID;
- subject ID;
- observed gallery revision;
- capture and creation timestamps;
- optional batch ID.

The SDK must preserve this attribution if connectivity is lost after capture and
synchronize it with the submission. The application reports the enrollment as
awaiting synchronization until Tanda Campus returns its canonical decision.
The SDK does not permit a new enrollment operation to begin without online
authorization.

The subject must remain an active member of the synchronized gallery. If Tanda Campus
has just created a minimal subject but its membership has not reached the local
replica, the SDK returns a stable `SubjectNotProvisioned` or
`GallerySyncRequired` outcome. The application synchronizes and reruns the
readiness check before requesting a biometric capture; enrollment rechecks the
membership to close synchronization races.

### 5. Kotlin/UniFFI API

Update the current API directly; backward-compatible student aliases are not
required before first deployment.

- Rename student-specific methods and records to subject terminology.
- Return the complete atomic identification evidence.
- Add a local enrollment-readiness check so the app can confirm writer authority
  and synchronized subject membership before requesting a biometric capture.
- Add the server-issued batch context to group-batch creation and a
  subject-specific enrollment authorization to every enrollment command.
- Revalidate membership during enrollment and expose stable
  `SubjectNotProvisioned`/`GallerySyncRequired` handling for synchronization
  races.
- Retain explicit sync, pending-enrollment count, credential rotation, and
  stable error categories.
- Treat the provisioned physical instance ID and bearer credential as opaque;
  logical device naming and authentication policy remain Tanda Campus concerns.
- The bearer credential is long-lived until online rotation or revocation; the
  SDK does not implement user login, user-session refresh, or device refresh
  tokens.
- Update the Android integration guide and examples to remove short-lived-token
  and offline-initiated-enrollment behavior.

### 6. Server FFI

Update the current C ABI and header directly:

- rename `student_id` fields and documentation to `subject_id`;
- apply duplicate checks by canonical subject identity;
- retain accepted, invalid-artifact, duplicate, and internal-error outcomes;
- retain panic containment, borrowed inputs, and explicit diagnostic cleanup.

A parallel v2 entry point is not required because there is no deployed
consumer. The Go adapter, header, package metadata, and cross-repository tests
must change in the same delivery.

### 7. Capacity and security validation

- Retain fixed-population galleries rather than introducing
  whole-organization search by default.
- Benchmark the largest expected fixed population on representative
  Android hardware.
- Measure bootstrap time, changed/no-change sync, matcher rebuild, memory, and
  identification latency.
- Confirm the encrypted-at-rest strategy for each private gallery database and
  extracted template artifacts before production deployment.
- Continue to require TLS, scoped device credentials, credential rotation, and
  private application storage.

Face recognition remains unimplemented even though the current SQL constraint
reserves the value. It requires a separate extractor, artifact, matcher, mobile,
and server-validation implementation and is not part of this update.

## Delivery Sequence

1. Agree final subject and gallery-population terminology with Tanda Campus.
2. Change the synchronized schema in both repositories.
3. Generalize Rust attendance and artifact contracts.
4. Add atomic match evidence.
5. Add bounded online enrollment authorization and durable attribution.
6. Add enrollment-readiness and stable subject-provisioning outcomes.
7. Update Kotlin/UniFFI and Android documentation, including long-lived device
   credential and online-only enrollment guidance.
8. Update the C ABI and Go adapter.
9. Rebuild cross-repository enrollment and synchronization fixtures.
10. Run Rust, Kotlin-binding, Go, PostgreSQL, and cross-repository tests.
11. Run Android capacity and offline-recovery validation.

## Acceptance Criteria

- Academic student-class and employee-group galleries, and other fixed-population
  galleries, can be provisioned independently.
- Minimally registered and profile-complete subjects enroll through the same SDK
  path once their canonical membership is synchronized.
- A subject absent from the local gallery produces a stable readiness/enrollment
  outcome and cannot bypass gallery membership.
- Form and bulk profile registration require no SDK profile storage or import
  API.
- Several physical instances can be activated under one logical device ID
  without sharing local files, credentials, or synchronization state.
- Every physical SDK instance opens and searches exactly one fixed gallery.
- A class gallery works unchanged when its device is assigned to a classroom,
  hostel, dining hall, assembly point, or gate.
- Student and employee subjects can be enrolled without student-specific SDK
  contracts.
- Successful identification returns complete, internally consistent attendance
  evidence.
- Enrollment submissions retain the administrator, device, subject, time, and
  gallery revision through a post-capture connection loss.
- Reader devices cannot enroll; revoked writers cannot submit further changes.
- A resumable group batch cannot authorize a new capture while the Admin is
  offline.
- Enrollment authorization cannot be replayed across subjects, batches,
  galleries, or physical instances, and Tanda Campus remains the canonical
  source of actor attribution.
- Duplicate validation works across the server-selected canonical identity
  scope.
- Enrollment cannot start offline, but an online-authorized capture survives a
  connection loss and later synchronizes safely.
- Freshly bootstrapped SDK and Tanda Campus schemas interoperate.
- Long-lived instance credentials support offline matching and later sync,
  rotate safely online, and require no user or device refresh-token flow.
- No raw fingerprint images are persisted.
- All repository and cross-repository tests pass.
