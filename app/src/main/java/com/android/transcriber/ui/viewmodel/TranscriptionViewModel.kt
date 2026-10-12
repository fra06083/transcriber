package com.android.transcriber.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.transcriber.TranscriberApp
import com.android.transcriber.data.model.TranscriptionRecord
import com.android.transcriber.domain.audio.AudioConverter
import com.android.transcriber.domain.audio.GoogleSpeechTranscriber
import com.android.transcriber.domain.audio.WhisperTranscriber
import com.android.transcriber.domain.updater.AppReleaseInfo
import com.android.transcriber.domain.updater.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpdateAvailable(val info: AppReleaseInfo) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as TranscriberApp).database
    private val audioConverter = AudioConverter(application)
    private val whisperTranscriber = WhisperTranscriber(application)
    private val googleSpeechTranscriber = GoogleSpeechTranscriber(application)
    private val appUpdateManager = AppUpdateManager(application)

    private val _uiState = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Idle)
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("auto")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedEngine = MutableStateFlow(TranscriptionEngine.GOOGLE)
    val selectedEngine: StateFlow<TranscriptionEngine> = _selectedEngine.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _isAutoUpdateEnabled = MutableStateFlow(appUpdateManager.isAutoCheckEnabled())
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    val recentTranscriptions: StateFlow<List<TranscriptionRecord>> =
        database.transcriptionRecordDao().getAllRecords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Run background update check on start only if user opted in
        if (appUpdateManager.isAutoCheckEnabled()) {
            checkForUpdates(isManual = false)
        }
    }

    fun setSelectedLanguage(lang: String) {
        _selectedLanguage.value = lang
    }

    fun setSelectedEngine(engine: TranscriptionEngine) {
        _selectedEngine.value = engine
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        appUpdateManager.setAutoCheckEnabled(enabled)
        _isAutoUpdateEnabled.value = enabled
    }

    fun checkForUpdates(isManual: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isManual) {
                _updateState.value = UpdateUiState.Checking
            }
            val result = appUpdateManager.checkLatestRelease()
            result.fold(
                onSuccess = { info ->
                    if (info.isUpdateAvailable) {
                        _updateState.value = UpdateUiState.UpdateAvailable(info)
                    } else {
                        if (isManual) {
                            _updateState.value = UpdateUiState.UpToDate(info.currentVersion)
                        } else {
                            _updateState.value = UpdateUiState.Idle
                        }
                    }
                },
                onFailure = { ex ->
                    Log.w(TAG, "Update check failed: ${ex.message}")
                    if (isManual) {
                        _updateState.value = UpdateUiState.Error(
                            ex.localizedMessage ?: "Impossibile verificare gli aggiornamenti"
                        )
                    } else {
                        _updateState.value = UpdateUiState.Idle
                    }
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateUiState.Idle
    }

    fun downloadUpdate(downloadUrl: String) {
        appUpdateManager.openDownloadUrl(downloadUrl)
    }

    fun deleteRecord(record: TranscriptionRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            database.transcriptionRecordDao().deleteRecord(record)
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            database.transcriptionRecordDao().clearAll()
        }
    }

    fun processAudioUri(audioUri: Uri, sourceApp: String = "Transcriber") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(180_000L) {
                    val currentEngine = _selectedEngine.value
                    Log.i(TAG, "Processing audio: $audioUri (engine=${currentEngine.name}, app=$sourceApp, lang=${_selectedLanguage.value})")
                    _uiState.value = TranscriptionUiState.Converting

                    val conversionResult = audioConverter.convertToWav(audioUri)

                    conversionResult.fold(
                        onSuccess = { wavFile ->
                            try {
                                Log.i(TAG, "WAV generated (${wavFile.length()} bytes). Starting transcription...")
                                _uiState.value = TranscriptionUiState.Transcribing("")

                                val lang = _selectedLanguage.value
                                var actuallyUsedWhisper = (currentEngine == TranscriptionEngine.WHISPER)

                                val transcriptionResult = if (currentEngine == TranscriptionEngine.GOOGLE) {
                                    val googleRes = googleSpeechTranscriber.transcribeWav(wavFile, languageCode = lang) { progressText ->
                                        _uiState.value = TranscriptionUiState.Transcribing(progressText)
                                    }
                                    if (googleRes.isFailure) {
                                        val ex = googleRes.exceptionOrNull()
                                        Log.w(TAG, "Google speech failed (${ex?.message}), falling back to Whisper...")
                                        actuallyUsedWhisper = true
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
                                        Log.i(TAG, "Transcription completed: $resultText")
                                        _uiState.value = TranscriptionUiState.Success(resultText)

                                        // Persist transcription in history
                                        val record = TranscriptionRecord(
                                            text = resultText,
                                            timestamp = System.currentTimeMillis(),
                                            sourceApp = sourceApp,
                                            isWhisper = actuallyUsedWhisper
                                        )
                                        database.transcriptionRecordDao().insertRecord(record)
                                    },
                                    onFailure = { exception ->
                                        Log.e(TAG, "Transcription error", exception)
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
                            Log.e(TAG, "FFmpeg conversion error", exception)
                            _uiState.value = TranscriptionUiState.Error(
                                exception.localizedMessage ?: "Errore durante la conversione FFmpeg dell'audio"
                            )
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Overall transcription timeout reached", e)
                _uiState.value = TranscriptionUiState.Error("Operazione scaduta: l'elaborazione ha impiegato più di 3 minuti.")
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error during transcription", t)
                _uiState.value = TranscriptionUiState.Error(t.localizedMessage ?: "Si è verificato un errore imprevisto.")
            }
        }
    }

    fun resetState() {
        _uiState.value = TranscriptionUiState.Idle
    }
}
