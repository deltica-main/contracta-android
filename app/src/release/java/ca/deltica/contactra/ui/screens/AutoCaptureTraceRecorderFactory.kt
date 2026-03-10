package ca.deltica.contactra.ui.screens

import android.content.Context

internal object AutoCaptureTraceRecorderFactory {
    fun create(
        context: Context,
        manifest: AutoCaptureTraceManifest
    ): AutoCaptureTraceRecorder {
        return DisabledAutoCaptureTraceRecorder
    }

    private object DisabledAutoCaptureTraceRecorder : AutoCaptureTraceRecorder {
        override val isEnabled: Boolean = false
        override fun record(record: AutoCaptureTraceRecord) = Unit
        override fun close() = Unit
    }
}
