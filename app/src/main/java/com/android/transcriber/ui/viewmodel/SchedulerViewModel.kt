package com.android.transcriber.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.transcriber.TranscriberApp
import com.android.transcriber.data.model.ScheduledMessage
import com.android.transcriber.data.repository.ScheduledMessageRepository
import com.android.transcriber.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScheduledMessageRepository
    private val alarmScheduler: AlarmScheduler

    val scheduledMessages: StateFlow<List<ScheduledMessage>>

    init {
        val dao = (application as TranscriberApp).database.scheduledMessageDao()
        repository = ScheduledMessageRepository(dao)
        alarmScheduler = AlarmScheduler(application)

        scheduledMessages = repository.allMessages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun scheduleMessage(phoneNumber: String, messageText: String, timeMillis: Long) {
        viewModelScope.launch {
            val newMsg = ScheduledMessage(
                phoneNumber = phoneNumber,
                messageText = messageText,
                scheduledTimeMillis = timeMillis
            )
            val generatedId = repository.insertMessage(newMsg)
            val savedMsg = newMsg.copy(id = generatedId)

            alarmScheduler.schedule(savedMsg)
        }
    }

    fun deleteMessage(message: ScheduledMessage) {
        viewModelScope.launch {
            alarmScheduler.cancel(message.id)
            repository.deleteMessage(message)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return alarmScheduler.canScheduleExactAlarms()
    }
}
