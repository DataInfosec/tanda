# Tanda

Tanda is a Kotlin Multiplatform app (Android + iOS) integrated with biometric fingerprint
enrollment and verification, using dedicated fingerprint scanner hardware on Android.
UI is shared across platforms with Compose Multiplatform.

## Project structure

The project is split into `core` modules (shared infrastructure), `feature` modules
(vertical slices of functionality), and the `app` module that wires everything together.

```
app/                        # Application entry point, DI wiring, navigation
core/
  common/                   # Shared utilities used across modules
  persistence/               # Local storage
  ui/                        # Shared Compose UI components/theming
feature/
  account/
    domain/ data/ ui/        # Account feature, split by layer
  preference/                 # App settings/preferences, including supported locales
  biometrics/
    domain/                   # Biometric use cases/interfaces
    data/                     # Biometric data layer
    device/                   # Platform-specific device access (Android/iOS)
    verification/             # Fingerprint matching, backed by a Rust core via UniFFI
    ui/                       # Biometric enrollment/verification UI
  scanner/
    libusb/                   # USB comms with the scanner hardware (Android-only)
    liblfd/                   # Live finger detection (Android-only, prebuilt .so libs)
    libibscancommon/          # Integrated Biometrics scanner SDK bindings (Android-only)
    libibscanuitimate/        # Integrated Biometrics scanner SDK bindings (Android-only)
ios/                         # iOS app shell (Xcode project) hosting the shared Compose UI
```

Most `core` and `feature` modules are true Kotlin Multiplatform modules (`commonMain`,
with `androidMain`/`iosMain` where platform code is needed). The `feature/scanner/*`
modules are Android-only libraries — they wrap vendor SDKs and native `.so` libraries
for the physical fingerprint scanner and are consumed only by the Android target of `app`.

`feature/biometrics/verification` bridges into a shared Rust biometric matching library
(pinned via `Cargo.toml` from an external `biometric-sdk` repo) and exposes it to Kotlin
through UniFFI-generated bindings.

## Tech stack

- **UI:** Compose Multiplatform, Navigation 3, Material 3 adaptive
- **DI:** Koin (with `koin-annotations` + KSP for compile-time generated modules)
- **Networking:** Ktor client
- **Persistence/Settings:** Multiplatform Settings, Okio
- **Serialization:** kotlinx.serialization, kotlinx.datetime
- **Native biometric matching:** Rust, packaged via `gobley` (Cargo/UniFFI Gradle plugins)

## Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar, or:

- Android app: `./gradlew :app:assembleDebug`
- iOS app: open [/ios](./ios) in Xcode and run it from there.

## Running tests

Run tests using the run button in your IDE's editor gutter, or via Gradle, e.g.
`./gradlew :feature:biometrics:verification:testDebugUnitTest`.

## Local configuration

Some build steps read secrets from `local.properties` in the project root. This file is
gitignored and must be created manually:

```properties
tanda.base.url=<base-url>
tanda.device.id=<device-id>
tanda.fingerprint.url=<fingerprint-url>
```

These values are picked up by `generateBuildConstants` (see below) and baked into a
generated `BuildConstants` object used by the app at runtime.

## Generated code tasks

A few Gradle tasks generate source code ahead of compilation. They normally run
automatically as part of `preBuild`/KSP, but can also be triggered manually:

- `./gradlew generateDepencencyMain` — runs KSP against the `commonMain` metadata to
  generate Koin dependency injection code for a module.
- `./gradlew generateBuildConstants` — reads `tanda.fingerprint.url` and
  `tanda.fingerprint.secret` from `local.properties` and generates the `BuildConstants`
  object consumed by `app`.
- `./gradlew generateSupportedLocale` — scans the existing `values*` resource folders
  under `composeResources` in a module (e.g. `feature/preference`) and generates a
  `SupportedLocale` list of the languages the app currently ships translations for.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…