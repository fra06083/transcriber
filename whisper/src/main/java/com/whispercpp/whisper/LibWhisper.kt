package com.whispercpp.whisper

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

private const val LOG_TAG = "LibWhisper"

class WhisperContext private constructor(private var ptr: Long) {
    // Meet Whisper C++ constraint: Don't access from more than one thread at a time.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    suspend fun transcribeData(
        data: FloatArray,
        language: String = "auto",
        printTimestamp: Boolean = false
    ): String = withContext(scope.coroutineContext) {
        require(ptr != 0L) { "WhisperContext has already been released or not initialized" }
        val numThreads = WhisperCpuConfig.preferredThreadCount
        Log.d(LOG_TAG, "Running transcribeData with $numThreads threads, lang='$language', samples=${data.size}")

        WhisperLib.fullTranscribe(ptr, numThreads, data, language)

        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return@withContext buildString {
            for (i in 0 until textCount) {
                val seg = WhisperLib.getTextSegment(ptr, i).trim()
                if (seg.isNotEmpty()) {
                    if (printTimestamp) {
                        val textTimestamp = "[${toTimestamp(WhisperLib.getTextSegmentT0(ptr, i))} --> ${toTimestamp(WhisperLib.getTextSegmentT1(ptr, i))}]"
                        append("$textTimestamp $seg\n")
                    } else {
                        if (isNotEmpty()) append(" ")
                        append(seg)
                    }
                }
            }
        }
    }

    suspend fun benchMemory(nthreads: Int): String = withContext(scope.coroutineContext) {
        return@withContext WhisperLib.benchMemcpy(nthreads)
    }

    suspend fun benchGgmlMulMat(nthreads: Int): String = withContext(scope.coroutineContext) {
        return@withContext WhisperLib.benchGgmlMulMat(nthreads)
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
    }

    protected fun finalize() {
        if (ptr != 0L) {
            try {
                WhisperLib.freeContext(ptr)
                ptr = 0L
            } catch (_: Exception) { }
        }
    }

    companion object {
        fun createContextFromFile(filePath: String): WhisperContext {
            val file = File(filePath)
            require(file.exists() && file.length() > 0) { "Model file does not exist or is empty: $filePath" }
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create Whisper context with path: $filePath")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromInputStream(stream: InputStream): WhisperContext {
            val ptr = WhisperLib.initContextFromInputStream(stream)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create Whisper context from input stream")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromAsset(assetManager: AssetManager, assetPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, assetPath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create Whisper context from asset: $assetPath")
            }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String {
            return WhisperLib.getSystemInfo()
        }
    }
}

internal class WhisperLib {
    companion object {
        init {
            val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else ""
            Log.d(LOG_TAG, "Primary ABI: $primaryAbi")

            // Ensure underlying ggml libraries are loaded first
            listOf("ggml-base", "ggml-cpu", "ggml").forEach { lib ->
                try {
                    System.loadLibrary(lib)
                    Log.d(LOG_TAG, "Pre-loaded native library: $lib")
                } catch (e: Throwable) {
                    Log.d(LOG_TAG, "Pre-load notice for $lib: ${e.message}")
                }
            }

            var loaded = false
            if (primaryAbi == "arm64-v8a") {
                try {
                    Log.d(LOG_TAG, "Attempting to load optimized libwhisper_v8fp16_va.so (ARMv8.2-A+FP16)")
                    System.loadLibrary("whisper_v8fp16_va")
                    Log.i(LOG_TAG, "Successfully loaded libwhisper_v8fp16_va.so")
                    loaded = true
                } catch (e: Throwable) {
                    Log.w(LOG_TAG, "Hardware FP16 library not available on this device (${e.message}), will load default whisper")
                }
            } else if (primaryAbi == "armeabi-v7a") {
                try {
                    val cpuInfo = cpuInfo()
                    if (cpuInfo?.contains("vfpv4") == true) {
                        Log.d(LOG_TAG, "Attempting to load libwhisper_vfpv4.so")
                        System.loadLibrary("whisper_vfpv4")
                        Log.i(LOG_TAG, "Successfully loaded libwhisper_vfpv4.so")
                        loaded = true
                    }
                } catch (e: Throwable) {
                    Log.w(LOG_TAG, "VFPv4 library failed (${e.message}), will load default whisper")
                }
            }

            if (!loaded) {
                try {
                    Log.d(LOG_TAG, "Loading default libwhisper.so")
                    System.loadLibrary("whisper")
                    Log.i(LOG_TAG, "Successfully loaded default libwhisper.so")
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "CRITICAL: Failed to load default libwhisper.so", e)
                }
            }
        }

        // JNI methods
        external fun initContextFromInputStream(inputStream: InputStream): Long
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            language: String
        )
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        external fun getSystemInfo(): String
        external fun benchMemcpy(nthread: Int): String
        external fun benchGgmlMulMat(nthread: Int): String
    }
}

private fun toTimestamp(t: Long, comma: Boolean = false): String {
    var msec = t * 10
    val hr = msec / (1000 * 60 * 60)
    msec -= hr * (1000 * 60 * 60)
    val min = msec / (1000 * 60)
    msec -= min * (1000 * 60)
    val sec = msec / 1000
    msec -= sec * 1000

    val delimiter = if (comma) "," else "."
    return String.format(java.util.Locale.US, "%02d:%02d:%02d%s%03d", hr, min, sec, delimiter, msec)
}

private fun cpuInfo(): String? {
    return try {
        File("/proc/cpuinfo").inputStream().bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Couldn't read /proc/cpuinfo", e)
        null
    }
}
