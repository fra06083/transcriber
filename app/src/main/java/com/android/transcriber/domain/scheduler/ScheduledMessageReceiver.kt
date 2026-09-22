package com.android.transcriber.domain.scheduler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.android.transcriber.TranscriberApp
import com.android.transcriber.data.model.ScheduledMessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (Intent.ACTION_BOOT_COMPLETED == action) {
            // Restore exact alarms after device reboot
            val repository = (context.applicationContext as TranscriberApp).database.scheduledMessageDao()
            val alarmScheduler = AlarmScheduler(context)

            CoroutineScope(Dispatchers.IO).launch {
                val pendingMessages = repository.getMessagesByStatus(ScheduledMessageStatus.PENDING)
                val now = System.currentTimeMillis()
                for (message in pendingMessages) {
                    if (message.scheduledTimeMillis > now) {
                        alarmScheduler.schedule(message)
                    } else {
                        repository.updateStatus(message.id, ScheduledMessageStatus.FAILED)
                    }
                }
            }
            return
        }

        val messageId = intent.getLongExtra("EXTRA_MESSAGE_ID", -1L)
        val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER") ?: ""
        val messageText = intent.getStringExtra("EXTRA_MESSAGE_TEXT") ?: ""

        if (phoneNumber.isBlank() || messageText.isBlank()) return

        val cleanPhone = phoneNumber.replace("+", "").replace(" ", "").replace("-", "").trim()

        runCatching {
            val encodedText = URLEncoder.encode(messageText, "UTF-8")
            val whatsappUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")

            val whatsappIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }

            try {
                context.startActivity(whatsappIntent)
            } catch (e: Exception) {
                // Fallback to general intent (e.g. WhatsApp Business or browser)
                val genericIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
            }

            sendNotification(context, cleanPhone, messageText)

            if (messageId != -1L) {
                val repository = (context.applicationContext as TranscriberApp).database.scheduledMessageDao()
                CoroutineScope(Dispatchers.IO).launch {
                    repository.updateStatus(messageId, ScheduledMessageStatus.SENT)
                }
            }
        }.onFailure {
            if (messageId != -1L) {
                val repository = (context.applicationContext as TranscriberApp).database.scheduledMessageDao()
                CoroutineScope(Dispatchers.IO).launch {
                    repository.updateStatus(messageId, ScheduledMessageStatus.FAILED)
                }
            }
        }
    }

    private fun sendNotification(context: Context, phone: String, text: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$phone"))
        val pendingIntent = PendingIntent.getActivity(
            context,
            phone.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "scheduled_messages_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Messaggio WhatsApp Inviato")
            .setContentText("Destinatario $phone: $text")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(phone.hashCode(), notification)
    }
}
