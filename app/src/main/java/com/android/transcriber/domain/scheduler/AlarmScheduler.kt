package com.android.transcriber.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.android.transcriber.data.model.ScheduledMessage

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(message: ScheduledMessage) {
        if (!canScheduleExactAlarms()) return

        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            putExtra("EXTRA_MESSAGE_ID", message.id)
            putExtra("EXTRA_PHONE_NUMBER", message.phoneNumber)
            putExtra("EXTRA_MESSAGE_TEXT", message.messageText)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            message.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                message.scheduledTimeMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                message.scheduledTimeMillis,
                pendingIntent
            )
        }
    }

    fun cancel(messageId: Long) {
        val intent = Intent(context, ScheduledMessageReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
