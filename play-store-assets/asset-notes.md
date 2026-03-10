# Contactra Play Store Asset Notes

## Screens used
- `screenshot-1.png`: `HomeScreen` (landing/workspace view with add-contact actions and quick search).
- `screenshot-2.png`: `ScanScreen` (Step 1 scan UI with framing guide, on-device processing status, and capture/import actions).
- `screenshot-3.png`: `ReviewScreen` (Step 2 OCR parsed-contact review with captured card preview and confidence indicators).
- `screenshot-4.png`: `ContactListScreen` (organized contacts directory with search/filter affordance and multiple saved contacts).

## UI/content tweaks made for store assets
- Added an `androidTest` screenshot harness to render real app composables with deterministic demo state.
- Used a safe in-test repository (`ScreenshotContactRepository`) with polished generic demo contacts (including John Doe / Northstar Industrial) and no personal/private data.
- Injected a realistic review-state payload (parsed fields, confidence values, and captured card bitmap) for `ReviewScreen`.
- Added a test-safe optional parameter to `ScanScreen`:
  - `requestCameraPermissionOnLaunch: Boolean = true` (default unchanged for production behavior)
  - Screenshot tests pass `false` to prevent runtime permission dialogs from interrupting deterministic capture.
- Generated feature graphic from app brand colors/logo plus real captured UI snippets; no promotional badges, pricing, or claims.

## Raw capture pipeline
- Raw captures are in `play-store-assets/raw-captures/`:
  - `01-home-workspace.png`
  - `02-scan-flow.png`
  - `03-review-parsed-contact.png`
  - `04-contacts-filtered-list.png`

## Final outputs and exact dimensions
- `feature-graphic-1024x500.png`: `1024 x 500`
- `screenshot-1.png`: `1080 x 1848`
- `screenshot-2.png`: `1080 x 1848`
- `screenshot-3.png`: `1080 x 1848`
- `screenshot-4.png`: `1080 x 1848`

## Remaining manual step (only for regeneration)
- None for this run.
- To regenerate on another machine, connect an Android device/emulator and rerun the instrumentation class:
  - `ca.deltica.contactra.ui.assets.PlayStoreAssetCaptureTest#capturePlayStoreScreens`
