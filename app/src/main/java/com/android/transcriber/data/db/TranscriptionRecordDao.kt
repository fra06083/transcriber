package com.android.transcriber.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.transcriber.data.model.TranscriptionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionRecordDao {

    @Query("SELECT * FROM transcription_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<TranscriptionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TranscriptionRecord): Long

    @Delete
    suspend fun deleteRecord(record: TranscriptionRecord)

    @Query("DELETE FROM transcription_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transcription_records")
    suspend fun clearAll()
}
