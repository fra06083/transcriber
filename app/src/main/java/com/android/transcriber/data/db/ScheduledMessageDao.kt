package com.android.transcriber.data.db

import androidx.room.*
import com.android.transcriber.data.model.ScheduledMessage
import com.android.transcriber.data.model.ScheduledMessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTimeMillis ASC")
    fun getAllMessages(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): ScheduledMessage?

    @Query("SELECT * FROM scheduled_messages WHERE status = :status ORDER BY scheduledTimeMillis ASC")
    suspend fun getMessagesByStatus(status: ScheduledMessageStatus): List<ScheduledMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ScheduledMessage): Long

    @Update
    suspend fun updateMessage(message: ScheduledMessage)

    @Delete
    suspend fun deleteMessage(message: ScheduledMessage)

    @Query("UPDATE scheduled_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScheduledMessageStatus)
}
