package com.whispercpp.whisper

import android.util.Log

object WhisperCpuConfig {
    private const val LOG_TAG = "WhisperCpuConfig"

    val preferredThreadCount: Int
        get() {
            val available = Runtime.getRuntime().availableProcessors()
            val threads = if (available >= 6) (available - 2).coerceIn(4, 6) else available.coerceAtLeast(2)
            Log.d(LOG_TAG, "Available processors: $available -> Allocated Whisper threads: $threads")
            return threads
        }
}
