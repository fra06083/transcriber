package com.android.transcriber.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.transcriber.domain.audio.AudioConverter
import com.android.transcriber.domain.audio.GoogleSpeechTranscriber
import com.android.transcriber.domain.audio.WhisperTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "TranscriptionViewModel"

enum class TranscriptionEngine(val displayName: String) {
    GOOGLE("Google Speech"),
    WHISPER("Whisper Turbo")
}

sealed interface TranscriptionUiState {
    object Idle : TranscriptionUiState
    object Converting : TranscriptionUiState
    data class Transcribing(val partialText: String = "") : TranscriptionUiState
    data class Success(val text: String) : TranscriptionUiState
    data class Error(val message: String) : TranscriptionUiState
}

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val audioConverter = AudioConverter(application)
    private val whisperTranscriber = WhisperTranscriber(application)
    private val googleSpeechTranscriber = GoogleSpeechTranscriber(application)

    private val _uiState = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Idle)
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("auto")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedEngine = MutableStateFlow(TranscriptionEngine.GOOGLE)
    val selectedEngine: StateFlow<TranscriptionEngine> = _selectedEngine.asStateFlow()

    fun setSelectedLanguage(lang: String) {
        _selectedLanguage.value = lang
    }

    fun setSelectedEngine(engine: TranscriptionEngine) {
        _selectedEngine.value = engine
    }

    fun processAudioUri(audioUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(180_000L) { // 3 minute maximum overall timeout
                    val currentEngine = _selectedEngine.value
                    Log.i(TAG, "Avvio elaborazione audio: $audioUri (motore: ${currentEngine.name}, lingua: ${_selectedLanguage.value})")
                    _uiState.value = TranscriptionUiState.Converting

                    val conversionResult = audioConverter.convertToWav(audioUri)

                    conversionResult.fold(
                        onSuccess = { wavFile ->
                            try {
                                Log.i(TAG, "File WAV generato (${wavFile.length()} bytes). Avvio trascrizione con ${currentEngine.displayName}...")
                                _uiState.value = TranscriptionUiState.Transcribing("")

                                val lang = _selectedLanguage.value
                                val transcriptionResult = if (currentEngine == TranscriptionEngine.GOOGLE) {
                                    val googleRes = googleSpeechTranscriber.transcribeWav(wavFile, languageCode = lang) { progressText ->
                                        _uiState.value = TranscriptionUiState.Transcribing(progressText)
                                    }
                                    if (googleRes.isFailure) {
                                        val ex = googleRes.exceptionOrNull()
                                        Log.w(TAG, "Google Speech non riuscito (${ex?.message}), fallback automatico su Whisper Turbo...")
                                        _uiState.value = TranscriptionUiState.Transcribing("Passaggio automatico a Whisper Turbo...")
                                        whisperTranscriber.transcribeWav(wavFile, languageCode = lang) { progressText ->
                                            _uiState.value = TranscriptionUiState.Transcribing(progressText)
                                        }
                                    } else {
                                        googleRes
                                    }
                                } else {
                                    whisperTranscriber.transcribeWav(wavFile, languageCode = lang) { progressText ->
                                        _uiState.value = TranscriptionUiState.Transcribing(progressText)
                                    }
                                }

                                transcriptionResult.fold(
                                    onSuccess = { resultText ->
                                        Log.i(TAG, "Trascrizione completata con successo: $resultText")
                                        _uiState.value = TranscriptionUiState.Success(resultText)
                                    },
                                    onFailure = { exception ->
                                        Log.e(TAG, "Errore nella trascrizione", exception)
                                        _uiState.value = TranscriptionUiState.Error(
                                            exception.localizedMessage ?: "Errore durante la trascrizione dell'audio"
                                        )
                                    }
                                )
                            } finally {
                                if (wavFile.exists()) {
                                    wavFile.delete()
                                }
                            }
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "Errore nella conversione FFmpeg", exception)
                            _uiState.value = TranscriptionUiState.Error(
                                exception.localizedMessage ?: "Errore durante la conversione FFmpeg dell'audio"
                            )
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timeout generale raggiunto durante l'elaborazione dell'audio", e)
                _uiState.value = TranscriptionUiState.Error("Operazione scaduta: l'elaborazione ha impiegato più di 3 minuti.")
            } catch (t: Throwable) {
                Log.e(TAG, "Errore inaspettato durante il processamento", t)
                _uiState.value = TranscriptionUiState.Error(t.localizedMessage ?: "Si è verificato un errore imprevisto.")
            }
        }
    }

    fun resetState() {
        _uiState.value = TranscriptionUiState.Idle
    }
}
