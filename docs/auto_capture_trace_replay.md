# Auto Capture Trace And Replay Workflow

## 1. Enable trace capture in debug build

Tracing is disabled by default. It is enabled only when this flag file exists in app private storage.

```bash
adb shell run-as com.example.businesscardscanner sh -c "mkdir -p files/auto_capture_traces && touch files/auto_capture_traces/enable.flag"
```

To disable tracing again:

```bash
adb shell run-as com.example.businesscardscanner rm -f files/auto_capture_traces/enable.flag
```

## 2. Run a scan session

Open the debug app and run scans in `ScanScreen`.

Each session writes:

- `trace.jsonl`
- `manifest.json`

Location in app private storage:

- `files/auto_capture_traces/<session_id>/trace.jsonl`
- `files/auto_capture_traces/<session_id>/manifest.json`

## 3. Export traces for offline replay

List session folders:

```bash
adb shell run-as com.example.businesscardscanner ls files/auto_capture_traces
```

Copy traces to shared storage then pull:

```bash
adb shell run-as com.example.businesscardscanner sh -c "cp -r files/auto_capture_traces /sdcard/Download/auto_capture_traces"
adb pull /sdcard/Download/auto_capture_traces ./auto_capture_traces
```

## 4. Add trace fixtures for unit tests

Drop a captured trace jsonl into:

- `app/src/test/resources/auto_capture_traces/`

Example fixture names used by replay tests:

- `handheld_hesitant_case.jsonl`
- `table_success_case.jsonl`

## 5. Run replay tests

Run targeted replay tests:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.businesscardscanner.ui.screens.AutoCaptureTraceReplayRunnerTest"
```

Run all unit tests:

```bash
./gradlew :app:testDebugUnitTest
```
