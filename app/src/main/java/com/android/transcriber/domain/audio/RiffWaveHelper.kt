package com.android.transcriber.domain.audio

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RiffWaveHelper {

    private const val TAG = "RiffWaveHelper"

    /**
     * Decodes a 16-bit PCM WAV file into a FloatArray of audio samples in [-1.0, 1.0].
     * Handles arbitrary RIFF chunk ordering (e.g. FFmpeg writing LIST chunk before 'data').
     */
    fun decodeWaveFile(file: File): FloatArray {
        require(file.exists() && file.length() >= 12) { "File WAV non valido o troppo piccolo: ${file.name}" }

        val bytes = file.readBytes()
        if (bytes.size < 12) {
            Log.w(TAG, "File WAV troppo piccolo: ${bytes.size} bytes")
            return FloatArray(0)
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") {
            Log.w(TAG, "Header non valido: riff=$riff, wave=$wave")
            return FloatArray(0)
        }

        var channels = 1
        var pcmOffset = -1
        var pcmLength = 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = buffer.getInt(offset + 4).toLong() and 0xFFFFFFFFL
            val dataStart = offset + 8

            if (chunkId == "fmt ") {
                channels = buffer.getShort(dataStart + 2).toInt().coerceAtLeast(1)
            } else if (chunkId == "data") {
                pcmOffset = dataStart
                pcmLength = if (chunkSize in 1..(bytes.size - dataStart).toLong()) {
                    chunkSize.toInt()
                } else {
                    bytes.size - dataStart
                }
                break
            }

            val nextOffset = dataStart + chunkSize + (chunkSize % 2)
            if (nextOffset <= offset || nextOffset > bytes.size) {
                break
            }
            offset = nextOffset.toInt()
        }

        // Fallback if data chunk not found by ID: assume standard 44-byte header
        if (pcmOffset == -1 || pcmLength <= 0) {
            pcmOffset = 44.coerceAtMost(bytes.size)
            pcmLength = bytes.size - pcmOffset
        }

        val shortCount = pcmLength / 2
        if (shortCount <= 0) {
            Log.w(TAG, "Nessun campione PCM trovato nel file WAV: pcmOffset=$pcmOffset, pcmLength=$pcmLength")
            return FloatArray(0)
        }

        buffer.position(pcmOffset)
        val shortBuffer = buffer.asShortBuffer()
        val shortArray = ShortArray(shortCount)
        shortBuffer.get(shortArray)

        Log.i(TAG, "Decodificati $shortCount campioni PCM (canali=$channels) da ${file.name}")

        val floatSamples = if (channels == 1) {
            FloatArray(shortCount) { i ->
                (shortArray[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
            }
        } else {
            val samples = shortCount / channels
            FloatArray(samples) { i ->
                var sum = 0f
                for (ch in 0 until channels) {
                    sum += shortArray[i * channels + ch] / 32768.0f
                }
                (sum / channels).coerceIn(-1.0f, 1.0f)
            }
        }

        // Normalizzazione del picco audio per massimizzare l'accuratezza del riconoscimento neurale Whisper
        var maxAbs = 0f
        for (s in floatSamples) {
            val abs = kotlin.math.abs(s)
            if (abs > maxAbs) maxAbs = abs
        }

        if (maxAbs > 0.01f && maxAbs < 0.75f) {
            val gain = (0.85f / maxAbs).coerceAtMost(6.0f)
            Log.i(TAG, "Audio normalizzato: picco originale=${"%.3f".format(maxAbs)}, applicato guadagno=${"%.2f".format(gain)}x")
            for (i in floatSamples.indices) {
                floatSamples[i] = (floatSamples[i] * gain).coerceIn(-1.0f, 1.0f)
            }
        }

        return floatSamples
    }
}
