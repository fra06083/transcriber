package com.android.transcriber.domain.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

private const val TAG = "AudioConverter"

class AudioConverter(private val context: Context) {

    suspend fun convertToWav(audioUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(30_000L) { // 30 second maximum timeout for audio conversion
                val cacheDir = context.cacheDir
                val ts = System.currentTimeMillis()
                val tempInputFile = File(cacheDir, "input_$ts.tmp")
                val outputFile = File(cacheDir, "output_$ts.wav")

                Log.i(TAG, "Copia dell'audio in arrivo in temp file: ${tempInputFile.absolutePath}")
                context.contentResolver.openInputStream(audioUri)?.use { input ->
                    FileOutputStream(tempInputFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Impossibile aprire il file audio dalla sorgente")

                if (!tempInputFile.exists() || tempInputFile.length() == 0L) {
                    tempInputFile.delete()
                    throw IllegalStateException("File audio vuoto o non trovato")
                }

                Log.i(TAG, "Avvio conversione FFmpeg da ${tempInputFile.length()} bytes...")

                val cmd = "-y -i \"${tempInputFile.absolutePath}\" -vn -ar 16000 -ac 1 -c:a pcm_s16le \"${outputFile.absolutePath}\""

                val session = suspendCancellableCoroutine { continuation ->
                    val asyncSession = FFmpegKit.executeAsync(
                        cmd,
                        { completedSession ->
                            if (continuation.isActive) {
                                continuation.resume(completedSession)
                            }
                        },
                        { log ->
                            Log.d(TAG, "FFmpeg: ${log.message}")
                        },
                        null
                    )
                    continuation.invokeOnCancellation {
                        Log.w(TAG, "Conversione FFmpeg cancellata o andata in timeout")
                        FFmpegKit.cancel(asyncSession.sessionId)
                    }
                }

                tempInputFile.delete()

                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.i(TAG, "Conversione FFmpeg completata con successo: ${outputFile.length()} bytes")
                        outputFile
                    } else {
                        throw IllegalStateException("Conversione completata ma il file WAV risultante è vuoto")
                    }
                } else {
                    val logs = session.allLogsAsString
                    Log.e(TAG, "Errore FFmpeg (codice $returnCode): $logs")
                    throw IllegalStateException("Conversione FFmpeg fallita (codice $returnCode): $logs")
                }
            }
        }
    }
}
