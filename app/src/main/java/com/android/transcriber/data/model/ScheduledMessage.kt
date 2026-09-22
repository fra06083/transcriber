package com.android.transcriber.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_messages")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val messageText: String,
    val scheduledTimeMillis: Long,
    val status: ScheduledMessageStatus = ScheduledMessageStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
