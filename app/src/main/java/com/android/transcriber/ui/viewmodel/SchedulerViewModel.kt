package com.android.transcriber.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.transcriber.TranscriberApp
import com.android.transcriber.data.model.ScheduledMessage
import com.android.transcriber.data.repository.ScheduledMessageRepository
import com.android.transcriber.domain.scheduler.AlarmScheduler
import android.content.Context
import android.content.Intent
import com.android.transcriber.domain.contact.ContactHelper
import com.android.transcriber.domain.contact.ContactItem
import com.android.transcriber.domain.scheduler.ScheduledMessageReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScheduledMessageRepository
    private val alarmScheduler: AlarmScheduler

    val scheduledMessages: StateFlow<List<ScheduledMessage>>

    private val _contactSearchResults = MutableStateFlow<List<ContactItem>>(emptyList())
    val contactSearchResults: StateFlow<List<ContactItem>> = _contactSearchResults.asStateFlow()

    private val _isSearchingContacts = MutableStateFlow(false)
    val isSearchingContacts: StateFlow<Boolean> = _isSearchingContacts.asStateFlow()

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

    fun searchContacts(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingContacts.value = true
            val results = ContactHelper.searchContacts(getApplication(), query)
            _contactSearchResults.value = results
            _isSearchingContacts.value = false
        }
    }

    fun scheduleMessage(phoneNumber: String, contactName: String?, messageText: String, timeMillis: Long) {
        viewModelScope.launch {
            val newMsg = ScheduledMessage(
                phoneNumber = phoneNumber.trim(),
                contactName = contactName?.trim()?.ifBlank { null },
                messageText = messageText.trim(),
                scheduledTimeMillis = timeMillis
            )
            val generatedId = repository.insertMessage(newMsg)
            val savedMsg = newMsg.copy(id = generatedId)

            alarmScheduler.schedule(savedMsg)
        }
    }

    fun sendNow(message: ScheduledMessage, context: Context) {
        viewModelScope.launch {
            alarmScheduler.cancel(message.id)
            val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
                putExtra("EXTRA_MESSAGE_ID", message.id)
                putExtra("EXTRA_PHONE_NUMBER", message.phoneNumber)
                putExtra("EXTRA_CONTACT_NAME", message.contactName)
                putExtra("EXTRA_MESSAGE_TEXT", message.messageText)
            }
            context.sendBroadcast(intent)
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
