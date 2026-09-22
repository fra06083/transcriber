package com.android.transcriber.domain.audio

import android.content.Context
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "WhisperTranscriber"

class WhisperTranscriber(private val context: Context) {

    private val modelManager = ModelManager(context)
    private var whisperContext: WhisperContext? = null
    private val mutex = Mutex()

    private suspend fun getOrInitContext(onProgress: (String) -> Unit = {}): WhisperContext = mutex.withLock {
        whisperContext?.let { return it }

        val modelFile = modelManager.getOrExtractModel(onProgress)
        Log.i(TAG, "Initializing WhisperContext with model: ${modelFile.absolutePath}")
        val newContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
        whisperContext = newContext
        newContext
    }


    /**
     * Transcribes a 16kHz mono WAV file using whisper.cpp.
     * Keeps the exact interface previously provided by VoskTranscriber.
     *
     * @param wavFile The WAV file to transcribe (16kHz mono PCM 16-bit).
     * @param languageCode "it" (default for Italian), "auto" for auto detection, "en" for English.
     * @param onProgress Callback invoked with partial text / progress status updates.
     */
    suspend fun transcribeWav(
        wavFile: File,
        languageCode: String = "it",
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(wavFile.exists() && wavFile.length() > 0) { "File WAV non valido o inesistente: ${wavFile.name}" }

            val ctx = getOrInitContext(onProgress)

            val audioData = RiffWaveHelper.decodeWaveFile(wavFile)
            Log.i(TAG, "Audio decodificato: ${audioData.size} campioni (${"%.2f".format(audioData.size / 16000.0)}s)")
            if (audioData.isEmpty()) {
                Log.w(TAG, "Nessun campione estratto dal file ${wavFile.name} (dimensione: ${wavFile.length()} bytes)")
                return@runCatching "Trascrizione completata: Nessun campione audio rilevato nel file."
            }

            val whisperLang = when (languageCode.lowercase().trim()) {
                "it" -> "it"
                "en" -> "en"
                else -> "auto"
            }

            val startTime = System.currentTimeMillis()
            Log.i(TAG, "Avvio elaborazione neurale Whisper (lang=$whisperLang)...")

            val transcribed = ctx.transcribeData(
                data = audioData,
                language = whisperLang,
                printTimestamp = false
            )

            val durationMs = System.currentTimeMillis() - startTime
            val finalText = transcribed.trim()
            Log.i(TAG, "Elaborazione Whisper completata in ${durationMs}ms: '$finalText'")

            if (finalText.isEmpty()) {
                "Trascrizione completata: Nessun testo rilevato nel file audio."
            } else {
                finalText
            }
        }
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        mutex.withLock {
            whisperContext?.release()
            whisperContext = null
        }
    }
}

/**
 * Backward compatibility typealias ensuring any external references to VoskTranscriber
 * continue to compile and work transparently with the Whisper engine.
 */
typealias VoskTranscriber = WhisperTranscriber
