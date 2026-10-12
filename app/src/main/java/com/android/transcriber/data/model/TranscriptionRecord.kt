package com.android.transcriber.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcription_records")
data class TranscriptionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String, // "WhatsApp", "Telegram", "Transcriber", ecc.
    val isWhisper: Boolean = false // false = Google Speech, true = Whisper Turbo
)
