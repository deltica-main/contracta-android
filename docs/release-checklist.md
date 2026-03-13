# Release Checklist

## Preflight

- Confirm Android toolchain:
  - Android Gradle Plugin: `9.1.0`
  - Gradle Wrapper: `9.3.1`
  - Build JDK: `17` (`org.gradle.java.home` points to `C:\\Program Files\\Java\\jdk-17`)
- Confirm app identity:
  - `applicationId`: `ca.deltica.contactra`
  - `versionCode`: `5`
  - `versionName`: `1.0.5`
  - `minSdk`: `24`
  - `targetSdk`: `36`
  - `compileSdk`: `36`

## Signing Inputs

Provide signing inputs through environment variables or `local.properties`:

- `CONTACTRA_KEYSTORE_FILE`
- `CONTACTRA_KEY_ALIAS`
- `CONTACTRA_STORE_PASSWORD`
- `CONTACTRA_KEY_PASSWORD`

## Reproducible Build Commands

Run from project root:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:clean :app:assembleDebug :app:assembleRelease :app:bundleRelease --no-daemon
```

## Artifact Checks

- Verify APKs:
  - `app/build/outputs/apk/debug/app-debug.apk`
  - `app/build/outputs/apk/release/app-release-unsigned.apk` (or `app-release.apk` when signing is configured)
- Verify AAB:
  - `app/build/outputs/bundle/release/app-release.aab`
- Verify AAB is signed with your upload key:
  - `jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab`
- Verify launcher icon + round icon are present in manifest.
- Verify release is minified and resource shrinking is enabled.

## Play Console Readiness

- Upload `app-release.aab` to internal testing first.
- Confirm Data safety answers match app behavior (camera, contacts, location access).
- Confirm privacy policy URL is published and matches the listed support/legal pages.
- Validate release notes and rollout strategy before production.
