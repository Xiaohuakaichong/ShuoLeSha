package com.example.shuolesa.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val title: String = "随身记录",
    val captureState: String = "CLOSED",
    val processingState: String = "PENDING",
    val sourceRevision: Int = 0,
    val sourcePath: String? = null,
    val error: String? = null,
    /** v4.2 (schema 8): `{"<speaker key>":"名字"}`, see ui/logic/SpeakerNames. Stays on the device. */
    val speakerNamesJson: String? = null,
)

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val chunkId: Long,
    val text: String,
    val provenance: String,
    val createdAt: Long,
    /** v4.1-a (schema 7): reserved for per-sentence timestamps; always null in v4.1-a. */
    val segmentsJson: String? = null,
)

@Fts4(contentEntity = TranscriptEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "transcripts_fts")
data class TranscriptFtsEntity(val text: String)

@Entity(tableName = "memory_topics", indices = [Index("sessionId"), Index("createdAt")])
data class MemoryTopicEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val summary: String,
    val sourcesJson: String,
    val createdAt: Long,
    val revision: Int = 0,
    val hidden: Boolean = false,
    val actionsJson: String = "[]",
)

@Entity(tableName = "derived_notes")
data class DerivedNoteEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val summary: String,
    val sourcesJson: String,
    val templateId: String,
    val sourceRevision: Int,
    val createdAt: Long,
)

@Entity(tableName = "daily_digests")
data class DailyDigestEntity(
    @PrimaryKey val date: String,
    val zoneId: String,
    val title: String,
    val content: String,
    val sourcesJson: String,
    val sourceFingerprint: String,
    val createdAt: Long,
)

@Entity(tableName = "action_tasks", indices = [Index("sessionId"), Index("state")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val sessionId: String?,
    val text: String,
    val state: String,
    val sourcesJson: String,
    val createdAt: Long,
    val whenHint: String? = null,
    val sourceDeleted: Boolean = false,
)

/**
 * v4.2 (schema 8): a moment the user marked while recording (录音页「标记刚才这句」). [offsetMs] counts from the start
 * of the recording; the detail page maps it to a 片段 by the sealed 片段 durations.
 */
@Entity(tableName = "capture_marks", indices = [Index("sessionId")])
data class CaptureMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val offsetMs: Long,
    val createdAt: Long,
)

@Entity(tableName = "processing_jobs", indices = [Index("state")])
data class ProcessingJobEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val subjectId: String,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val error: String? = null,
    val owner: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val resultJson: String? = null,
)

data class TranscriptSource(
    val chunkId: Long,
    val text: String,
    val provenance: String,
    val createdAt: Long,
    val sessionId: String,
    val recordingMode: String?,
)

data class TranscriptMeta(val chunkId: Long, val provenance: String, val createdAt: Long, val textLength: Int)

/** v4.1.1: per-chunk timestamp slot without loading the transcript text. */
data class TranscriptSegments(val chunkId: Long, val segmentsJson: String?)
