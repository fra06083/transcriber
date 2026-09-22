package com.android.transcriber.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.android.transcriber.data.model.ScheduledMessage
import com.android.transcriber.data.model.ScheduledMessageStatus

class Converters {
    @TypeConverter
    fun fromStatus(status: ScheduledMessageStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ScheduledMessageStatus =
        runCatching { ScheduledMessageStatus.valueOf(value) }.getOrDefault(ScheduledMessageStatus.PENDING)
}

@Database(entities = [ScheduledMessage::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduledMessageDao(): ScheduledMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transcriber_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
