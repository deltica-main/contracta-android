# Contactra 1.0.5 (Draft)

## Build and Release

- Upgraded Android Gradle Plugin from `9.0.0` to `9.1.0`.
- Upgraded Gradle Wrapper from `9.1.0` to `9.3.1`.
- Standardized build runtime on JDK `17` for AGP compatibility.
- Aligned Java/Kotlin bytecode targets to `17`.
- Upgraded Room to `2.8.4` to resolve Kotlin metadata compatibility in KAPT.

## App Packaging

- Bumped app version to:
  - `versionCode 5`
  - `versionName 1.0.5`
- Added explicit launcher icon and round icon references in the main manifest.
- Explicitly disabled cleartext traffic in the main manifest.
- Moved Compose UI tooling dependency to `debugImplementation` to keep preview tooling out of release artifacts.

## Behavior

- No intentional functional feature changes.
- Changes are focused on build tooling compatibility and release safety.
