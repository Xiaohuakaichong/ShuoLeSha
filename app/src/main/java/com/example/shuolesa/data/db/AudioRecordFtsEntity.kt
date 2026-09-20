package com.example.shuolesa.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 全文索引：覆盖 title / summary / transcription。
 * contentEntity 与 audio_records 联动，Room 在内容表变更时自动维护。
 */
@Fts4(contentEntity = AudioRecordEntity::class)
@Entity(tableName = "audio_records_fts")
data class AudioRecordFtsEntity(
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "summary")
    val summary: String?,
    @ColumnInfo(name = "transcription")
    val transcription: String?,
)
