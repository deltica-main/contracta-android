package com.example.businesscardscanner.ui.screens

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

internal object AutoCaptureTraceRecorderFactory {
    private const val TRACE_ROOT_DIR = "auto_capture_traces"
    private const val TRACE_ENABLE_FLAG = "auto_capture_traces/enable.flag"
    private const val TRACE_LOG_TAG = "AutoCaptureTrace"

    fun create(
        context: Context,
        manifest: AutoCaptureTraceManifest
    ): AutoCaptureTraceRecorder {
        if (!isTraceEnabled(context)) {
            return DisabledAutoCaptureTraceRecorder
        }
        return FileAutoCaptureTraceRecorder(context, manifest)
    }

    private fun isTraceEnabled(context: Context): Boolean {
        val isDebuggable =
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            return false
        }
        val flagFile = File(context.filesDir, TRACE_ENABLE_FLAG)
        return flagFile.exists()
    }

    private object DisabledAutoCaptureTraceRecorder : AutoCaptureTraceRecorder {
        override val isEnabled: Boolean = false
        override fun record(record: AutoCaptureTraceRecord) = Unit
        override fun close() = Unit
    }

    private class FileAutoCaptureTraceRecorder(
        context: Context,
        manifest: AutoCaptureTraceManifest
    ) : AutoCaptureTraceRecorder {
        override val isEnabled: Boolean = true

        private val writer: BufferedWriter?

        init {
            val traceRoot = File(context.filesDir, TRACE_ROOT_DIR)
            val sessionDir = File(traceRoot, manifest.sessionId)
            writer = try {
                if (!sessionDir.exists()) {
                    sessionDir.mkdirs()
                }
                val manifestFile = File(sessionDir, "manifest.json")
                manifestFile.writeText(AutoCaptureTraceJsonCodec.encodeManifest(manifest))
                val traceFile = File(sessionDir, "trace.jsonl")
                BufferedWriter(FileWriter(traceFile, true))
            } catch (io: IOException) {
                Log.w(TRACE_LOG_TAG, "trace_writer_init_failed", io)
                null
            }
        }

        @Synchronized
        override fun record(record: AutoCaptureTraceRecord) {
            val activeWriter = writer ?: return
            try {
                activeWriter.write(AutoCaptureTraceJsonCodec.encodeRecord(record))
                activeWriter.newLine()
                activeWriter.flush()
            } catch (io: IOException) {
                Log.w(TRACE_LOG_TAG, "trace_record_write_failed", io)
            }
        }

        @Synchronized
        override fun close() {
            val activeWriter = writer ?: return
            runCatching {
                activeWriter.flush()
                activeWriter.close()
            }.onFailure { t ->
                Log.w(TRACE_LOG_TAG, "trace_writer_close_failed", t)
            }
        }
    }
}
