package com.android.transcriber.domain.audio

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

private const val TAG = "GoogleSpeechTranscriber"

class GoogleSpeechTranscriber(private val context: Context) {

    // Transcribe 16kHz mono WAV file using Android SpeechRecognizer with chunking on silence
    suspend fun transcribeWav(
        wavFile: File,
        languageCode: String = "it",
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            require(wavFile.exists() && wavFile.length() > 44) {
                "Invalid or missing WAV file: ${wavFile.name}"
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                throw UnsupportedOperationException(
                    "Google Speech file streaming requires Android 13+. Device is on Android ${Build.VERSION.RELEASE}."
                )
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                throw IllegalStateException("Google Speech recognition is not available on this device.")
            }

            val chunks = sliceWavIntoChunks(wavFile, targetChunkDurationSec = 8)
            Log.i(TAG, "Audio split into ${chunks.size} chunks")

            val completedSegments = mutableListOf<String>()

            try {
                for ((index, chunk) in chunks.withIndex()) {
                    if (index > 0) {
                        // Allow recognition service to reset between chunks
                        delay(250)
                    }

                    Log.i(TAG, "Transcribing chunk ${index + 1}/${chunks.size}...")

                    val chunkResult = transcribeChunkWithRetry(chunk, languageCode) { partialInChunk ->
                        val currentRunning = buildString {
                            if (completedSegments.isNotEmpty()) {
                                append(completedSegments.joinToString(" "))
                                append(" ")
                            }
                            append(partialInChunk.trim())
                        }.trim()
                        if (currentRunning.isNotBlank()) {
                            onProgress(currentRunning)
                        }
                    }

                    val trimmed = chunkResult.trim()
                    if (trimmed.isNotBlank()) {
                        completedSegments.add(trimmed)
                        val totalSoFar = completedSegments.joinToString(" ")
                        onProgress(totalSoFar)
                    }
                }
            } finally {
                // Delete temporary chunk files
                for (chunk in chunks) {
                    if (chunk != wavFile && chunk.exists()) {
                        runCatching { chunk.delete() }
                    }
                }
            }

            val finalOutput = completedSegments.joinToString(" ").trim()
            if (finalOutput.isBlank()) {
                "Trascrizione completata: Nessun testo rilevato nel file audio."
            } else {
                finalOutput
            }
        }
    }

    // Transcribe single chunk with retry on busy error
    private suspend fun transcribeChunkWithRetry(
        chunkFile: File,
        languageCode: String,
        onPartial: (String) -> Unit
    ): String {
        var result = transcribeSingleChunk(chunkFile, languageCode, onPartial)
        if (result.isBlank()) {
            delay(300)
            result = transcribeSingleChunk(chunkFile, languageCode, onPartial)
        }
        return result
    }

    // Pipe audio chunk to SpeechRecognizer
    private suspend fun transcribeSingleChunk(
        chunkFile: File,
        languageCode: String,
        onPartial: (String) -> Unit
    ): String = withContext(Dispatchers.Main) {
        val recognizer: SpeechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        val deferredResult = CompletableDeferred<String>()
        var lastPartial = ""
        var streamingJob: Job? = null

        val targetLocale = when (languageCode.lowercase().trim()) {
            "it" -> Locale.ITALIAN
            "en" -> Locale.US
            else -> Locale.getDefault()
        }

        val chunkSec = ((chunkFile.length() - 44) / 32000L).coerceAtLeast(1L)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, chunkSec * 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)

            putExtra("android.speech.extra.AUDIO_SOURCE", readSide)
            putExtra("android.speech.extra.AUDIO_SOURCE_CHANNEL_COUNT", 1)
            putExtra("android.speech.extra.AUDIO_SOURCE_ENCODING", AudioFormat.ENCODING_PCM_16BIT)
            putExtra("android.speech.extra.AUDIO_SOURCE_SAMPLING_RATE", 16000)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Log.d(TAG, "SpeechRecognizer error: $error")
                if (!deferredResult.isCompleted) {
                    deferredResult.complete(lastPartial)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: lastPartial
                if (!deferredResult.isCompleted) {
                    deferredResult.complete(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) {
                    lastPartial = text
                    onPartial(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)

            // Stream PCM bytes to the pipe
            streamingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    FileInputStream(chunkFile).use { input ->
                        input.skip(44) // Skip WAV header
                        FileOutputStream(writeSide.fileDescriptor).use { output ->
                            val buffer = ByteArray(3200)
                            var read = 0
                            while (isActive && input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                output.flush()
                                delay(35) // ~2.8x realtime speed
                            }
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    runCatching { writeSide.close() }
                }
            }

            val maxTimeoutMs = ((chunkSec + 7) * 1000L).coerceAtLeast(9_000L)
            withTimeoutOrNull(maxTimeoutMs) {
                deferredResult.await()
            } ?: lastPartial
        } finally {
            streamingJob?.cancel()
            runCatching { writeSide.close() }
            runCatching { readSide.close() }
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
        }
    }

    // Split WAV file into chunks at lowest-energy points
    private fun sliceWavIntoChunks(wavFile: File, targetChunkDurationSec: Int = 8): List<File> {
        val tempDir = wavFile.parentFile ?: context.cacheDir
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return listOf(wavFile)

        val pcmBytes = bytes.copyOfRange(44, bytes.size)
        val totalPcmBytes = pcmBytes.size
        val bytesPerSec = 32000 // 16kHz 16-bit mono
        val totalDurationSec = totalPcmBytes / bytesPerSec

        if (totalDurationSec <= targetChunkDurationSec + 3) {
            return listOf(wavFile)
        }

        val chunks = mutableListOf<File>()
        var startByte = 0
        var chunkIndex = 0

        while (startByte < totalPcmBytes) {
            val remainingBytes = totalPcmBytes - startByte
            val remainingSec = remainingBytes / bytesPerSec

            if (remainingSec <= targetChunkDurationSec + 3) {
                // Final chunk
                val chunkBytes = pcmBytes.copyOfRange(startByte, totalPcmBytes)
                val chunkFile = File(tempDir, "chunk_${System.currentTimeMillis()}_$chunkIndex.wav")
                writeWavFile(chunkBytes, chunkFile)
                chunks.add(chunkFile)
                break
            }

            // Find lowest energy split window around target duration
            val minSplitByte = startByte + ((targetChunkDurationSec - 2.5f) * bytesPerSec).toInt()
            val maxSplitByte = (startByte + ((targetChunkDurationSec + 1.5f) * bytesPerSec).toInt())
                .coerceAtMost(totalPcmBytes - 32000)

            var bestSplitByte = startByte + (targetChunkDurationSec * bytesPerSec)
            var minEnergy = Long.MAX_VALUE

            var testOffset = minSplitByte
            while (testOffset + 3200 <= maxSplitByte) {
                var energy = 0L
                var i = 0
                while (i < 3200) {
                    val sample = (pcmBytes[testOffset + i + 1].toInt() shl 8) or (pcmBytes[testOffset + i].toInt() and 0xFF)
                    val s = sample.toShort()
                    energy += kotlin.math.abs(s.toInt())
                    i += 2
                }
                if (energy < minEnergy) {
                    minEnergy = energy
                    bestSplitByte = testOffset + 1600
                }
                testOffset += 1600
            }

            if (bestSplitByte % 2 != 0) bestSplitByte++
            bestSplitByte = bestSplitByte.coerceIn(startByte + 32000, totalPcmBytes)

            val chunkBytes = pcmBytes.copyOfRange(startByte, bestSplitByte)
            val chunkFile = File(tempDir, "chunk_${System.currentTimeMillis()}_$chunkIndex.wav")
            writeWavFile(chunkBytes, chunkFile)
            chunks.add(chunkFile)

            startByte = bestSplitByte
            chunkIndex++
        }

        return chunks
    }

    // Write PCM bytes with 44-byte WAV header
    private fun writeWavFile(pcmData: ByteArray, outputFile: File, sampleRate: Int = 16000) {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort()) // PCM format
            putShort(1.toShort()) // Mono
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2.toShort()) // Block align
            putShort(16.toShort()) // 16 bits per sample
            put("data".toByteArray())
            putInt(pcmData.size)
        }.array()

        outputFile.outputStream().use { fos ->
            fos.write(header)
            fos.write(pcmData)
        }
    }
}
