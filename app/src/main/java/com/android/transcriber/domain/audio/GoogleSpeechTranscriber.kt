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
import java.util.Locale

private const val TAG = "GoogleSpeechTranscriber"

class GoogleSpeechTranscriber(private val context: Context) {

    /**
     * Trascrive un file WAV 16kHz mono utilizzando lo SpeechRecognizer di sistema Android (Google Speech).
     * Su Android 13+ (API 33+) trasmette l'audio direttamente al riconoscitore tramite una pipe ParcelFileDescriptor.
     *
     * @param wavFile File WAV 16kHz mono 16-bit PCM.
     * @param languageCode "it", "en", o "auto".
     * @param onProgress Callback per gli aggiornamenti di testo parziale in tempo reale.
     */
    suspend fun transcribeWav(
        wavFile: File,
        languageCode: String = "it",
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            require(wavFile.exists() && wavFile.length() > 44) {
                "File audio non valido o inesistente: ${wavFile.name}"
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                throw UnsupportedOperationException(
                    "Lo streaming da file con Google Speech richiede Android 13 o superiore. " +
                            "Il tuo dispositivo ha Android ${Build.VERSION.RELEASE}: usa il motore Whisper IA."
                )
            }

            // Verifica disponibilità SpeechRecognizer
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                throw IllegalStateException(
                    "Nessun servizio di riconoscimento vocale Google disponibile sul dispositivo."
                )
            }

            // Crea il riconoscitore preferendo l'on-device se supportato
            val recognizer: SpeechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                Log.i(TAG, "Creazione SpeechRecognizer on-device dedicato")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                Log.i(TAG, "Creazione SpeechRecognizer standard di sistema")
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            val deferredResult = CompletableDeferred<String>()
            var partialAccumulated = ""
            var streamingJob: Job? = null

            val targetLocale = when (languageCode.lowercase().trim()) {
                "it" -> Locale.ITALIAN
                "en" -> Locale.US
                else -> Locale.getDefault()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

                // Extra per streaming audio da pipe (Android 13+ / API 33+)
                putExtra("android.speech.extra.AUDIO_SOURCE", readSide)
                putExtra("android.speech.extra.AUDIO_SOURCE_CHANNEL_COUNT", 1)
                putExtra("android.speech.extra.AUDIO_SOURCE_ENCODING", AudioFormat.ENCODING_PCM_16BIT)
                putExtra("android.speech.extra.AUDIO_SOURCE_SAMPLING_RATE", 16000)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.i(TAG, "Google Speech pronto a ricevere l'audio")
                }

                override fun onBeginningOfSpeech() {
                    Log.i(TAG, "Inizio rilevamento voce Google Speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.i(TAG, "Fine parlato rilevata da Google Speech")
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Errore di registrazione audio"
                        SpeechRecognizer.ERROR_CLIENT -> "Errore interno dell'applicazione"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permessi microfono/audio non concessi"
                        SpeechRecognizer.ERROR_NETWORK -> "Errore di connessione di Google Speech"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout di rete di Google Speech"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Nessuna parola riconosciuta nell'audio"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Servizio di riconoscimento vocale occupato"
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Disconnesso dal servizio di riconoscimento"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nessun parlato rilevato nell'audio"
                        else -> "Errore di riconoscimento vocale Google (codice $error)"
                    }
                    Log.w(TAG, "Google Speech onError: $errorMessage ($error)")

                    // Se avevamo già accumulato testo parziale, usiamo quello invece di fallire
                    if (partialAccumulated.isNotBlank()) {
                        deferredResult.complete(partialAccumulated)
                    } else {
                        deferredResult.completeExceptionally(Exception(errorMessage))
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestText = matches?.firstOrNull()?.trim() ?: partialAccumulated
                    Log.i(TAG, "Google Speech completato con testo: '$bestText'")
                    deferredResult.complete(bestText)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (!text.isNullOrEmpty()) {
                        partialAccumulated = text
                        Log.d(TAG, "Google Speech testo parziale: $text")
                        onProgress(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                Log.i(TAG, "Avvio ascolto Google Speech...")
                recognizer.startListening(intent)

                // Avvia lo streaming dei byte PCM del file WAV nella pipe su un thread I/O
                streamingJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        FileInputStream(wavFile).use { input ->
                            // Salta l'header WAV (44 byte) per inviare solo PCM 16-bit grezzo
                            input.skip(44)
                            FileOutputStream(writeSide.fileDescriptor).use { output ->
                                // Blocchi da 3200 byte (100ms di audio a 16kHz mono 16-bit = 32000 byte/s)
                                val buffer = ByteArray(3200)
                                var bytesRead = 0

                                while (isActive && input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    output.flush()
                                    // Streaming a 2x rispetto al tempo reale per non sovraccaricare la pipe
                                    delay(45)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Nota streaming pipe: ${e.localizedMessage}")
                    } finally {
                        runCatching { writeSide.close() }
                    }
                }

                // Attende il risultato con un timeout proporzionato alla durata dell'audio
                val audioDurationSec = (wavFile.length() - 44) / 32000L
                val maxTimeoutMs = ((audioDurationSec + 10) * 1000L).coerceAtLeast(30_000L)

                val finalResult = withTimeoutOrNull(maxTimeoutMs) {
                    deferredResult.await()
                } ?: run {
                    if (partialAccumulated.isNotBlank()) {
                        partialAccumulated
                    } else {
                        throw Exception("Timeout: Google Speech non ha restituito risultati entro $audioDurationSec secondi.")
                    }
                }

                if (finalResult.isBlank()) {
                    "Trascrizione completata: Nessun testo rilevato nel file audio."
                } else {
                    finalResult
                }
            } finally {
                streamingJob?.cancel()
                runCatching { writeSide.close() }
                runCatching { readSide.close() }
                recognizer.stopListening()
                recognizer.destroy()
            }
        }
    }
}
