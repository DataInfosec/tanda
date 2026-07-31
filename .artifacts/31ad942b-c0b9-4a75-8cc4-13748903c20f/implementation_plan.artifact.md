# Fix UniFFI Binding Generation Error

The user is encountering an error where the UniFFI binding generator cannot find the `biometric_sdk` crate within the compiled iOS library (`libbiometric_sdk.dylib`). This is primarily caused by missing feature definitions in `Cargo.toml`, which results in the UniFFI scaffolding code being omitted from the build.

## User Review Required

> [!IMPORTANT]
> The fix involves modifying `Cargo.toml` to define and enable the `kotlin-bindings` feature by default. This ensures that the necessary UniFFI metadata is embedded in the compiled library so that the binding generator can find it.

## Proposed Changes

### Biometrics Verification Feature

#### [MODIFY] [Cargo.toml](file:///Users/yakubbello/StudioProjects/tanda/feature/biometrics/verification/Cargo.toml)
- Add `[features]` section.
- Define `kotlin-bindings` and `binding-generator`.
- Set `kotlin-bindings` as a default feature to ensure it's included in the library build.

#### [MODIFY] [lib.rs](file:///Users/yakubbello/StudioProjects/tanda/feature/biometrics/verification/src/commonMain/rust/lib.rs)
- Explicitly pass the crate name to `uniffi::setup_scaffolding!("biometric_sdk")` to ensure consistency with the package name.

## Verification Plan

### Automated Tests
- Run `./gradlew :feature:biometrics:verification:buildUniffiBindings` to verify that the bindings are generated successfully.

### Manual Verification
- Check that `target/aarch64-apple-ios/debug/libbiometric_sdk.dylib` (if accessible) contains the expected symbols (though the successful gradle task is the primary indicator).
