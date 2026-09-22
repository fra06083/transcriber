package com.android.transcriber.domain.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        const val MODEL_QUANT_NAME = "ggml-small-q5_1.bin"
        const val MODEL_SMALL_NAME = "ggml-small.bin"
    }

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Retrieves the local model file from disk or extracts it directly from assets.
     * Operates 100% offline with zero network connectivity.
     */
    suspend fun getOrExtractModel(onProgress: (String) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val candidates = listOf(MODEL_QUANT_NAME, MODEL_SMALL_NAME)

        // 1. Check in assets/models/ to determine model and expected size
        for (name in candidates) {
            val assetPath = "models/$name"
            if (assetExists(assetPath)) {
                val targetFile = File(modelsDir, name)

                val assetLength = runCatching {
                    context.assets.openFd(assetPath).use { it.length }
                }.getOrDefault(-1L)
                val expectedLength = if (assetLength > 0) assetLength else 190_000_000L

                // If already completely extracted, return immediately
                if (targetFile.exists() && (assetLength > 0 && targetFile.length() == assetLength || targetFile.length() > 180 * 1024 * 1024)) {
                    Log.i(TAG, "Modello già presente e valido: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                    return@withContext targetFile
                }

                // Delete potentially corrupted partial extraction
                if (targetFile.exists()) {
                    Log.w(TAG, "File modello esistente incompleto o non corrispondente (${targetFile.length()} bytes vs expected $expectedLength), riestrazione...")
                    targetFile.delete()
                }

                onProgress("Inizializzazione modello IA offline (0%)...")
                val tempFile = File(modelsDir, "$name.tmp")
                if (tempFile.exists()) tempFile.delete()

                Log.i(TAG, "Estrazione asset '$assetPath' verso '${targetFile.absolutePath}' (dimensione stimata: $expectedLength bytes)")

                context.assets.open(assetPath).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(512 * 1024) // 512 KB buffer for high speed
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgress = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val percent = ((totalRead * 100) / expectedLength).toInt().coerceIn(0, 100)
                            if (percent != lastProgress && (percent % 10 == 0 || percent == 100)) {
                                lastProgress = percent
                                onProgress("Inizializzazione modello IA offline: $percent%")
                            }
                        }
                        output.flush()
                    }
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                Log.i(TAG, "Estrazione completata: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                onProgress("Caricamento modello completato")
                return@withContext targetFile
            }
        }

        // Check if file is available in internal storage as fallback
        for (name in candidates) {
            val file = File(modelsDir, name)
            if (file.exists() && file.length() > 100 * 1024 * 1024) {
                return@withContext file
            }
        }

        throw IllegalStateException("Nessun modello Whisper trovato negli assets o nella memoria locale.")
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { }
            true
        } catch (_: Exception) {
            false
        }
    }
}
