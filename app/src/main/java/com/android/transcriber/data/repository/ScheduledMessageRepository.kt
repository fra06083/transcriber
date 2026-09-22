package com.android.transcriber.data.repository

import com.android.transcriber.data.db.ScheduledMessageDao
import com.android.transcriber.data.model.ScheduledMessage
import com.android.transcriber.data.model.ScheduledMessageStatus
import kotlinx.coroutines.flow.Flow

class ScheduledMessageRepository(private val dao: ScheduledMessageDao) {

    val allMessages: Flow<List<ScheduledMessage>> = dao.getAllMessages()

    suspend fun getMessageById(id: Long): ScheduledMessage? {
        return dao.getMessageById(id)
    }

    suspend fun insertMessage(message: ScheduledMessage): Long {
        return dao.insertMessage(message)
    }

    suspend fun updateMessage(message: ScheduledMessage) {
        dao.updateMessage(message)
    }

    suspend fun deleteMessage(message: ScheduledMessage) {
        dao.deleteMessage(message)
    }

    suspend fun updateStatus(id: Long, status: ScheduledMessageStatus) {
        dao.updateStatus(id, status)
    }
}
