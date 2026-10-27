package com.example.shuolesa.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.data.model.TaskState
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Must equal @Database(version) below; QueueReuseTest.schemaConstantMatchesRoomVersion checks it. */
const val DB_SCHEMA_VERSION = 8

@Database(entities = [AudioRecordEntity::class, RecordingSessionEntity::class,
    TranscriptEntity::class, TranscriptFtsEntity::class, MemoryTopicEntity::class,
    DerivedNoteEntity::class, DailyDigestEntity::class, TaskEntity::class,
    ProcessingJobEntity::class, CaptureMarkEntity::class], version = 8, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioRecordDao(): AudioRecordDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_records ADD COLUMN recordingMode TEXT DEFAULT 'lifelog'")
                db.execSQL("ALTER TABLE audio_records ADD COLUMN audioFormat TEXT")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_records ADD COLUMN segmentsJson TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep every legacy row/id/path and reconcile fresh-v5 and migrated-v5 defaults.
                db.execSQL("""CREATE TABLE audio_records_v6 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL,
                    chunkIndex INTEGER NOT NULL, filePath TEXT NOT NULL, durationMs INTEGER NOT NULL,
                    fileSizeBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL,
                    uploadedAt INTEGER, retryCount INTEGER NOT NULL, title TEXT, summary TEXT,
                    actionItems TEXT, tags TEXT, transcription TEXT, agentResult TEXT,
                    recordingMode TEXT DEFAULT 'lifelog', audioFormat TEXT, segmentsJson TEXT,
                    sourceStartMs INTEGER, sourceEndMs INTEGER)""")
                db.execSQL("""INSERT INTO audio_records_v6
                    SELECT id,sessionId,chunkIndex,filePath,durationMs,fileSizeBytes,createdAt,status,
                    uploadedAt,retryCount,title,summary,actionItems,tags,transcription,agentResult,
                    recordingMode,audioFormat,segmentsJson,NULL,NULL FROM audio_records""")
                db.execSQL("DROP TABLE audio_records")
                db.execSQL("ALTER TABLE audio_records_v6 RENAME TO audio_records")
                createMemoryTables(db)
                migrateLegacyContent(db)
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS transcripts_fts USING FTS4(text, content=`transcripts`, tokenize=unicode61)")
                db.execSQL("INSERT INTO transcripts_fts(transcripts_fts) VALUES('rebuild')")
                db.execSQL("UPDATE audio_records SET status='PENDING' WHERE status='UPLOADING'")
            }
        }

        /** v4.1-a: nullable per-sentence timestamp slot for a future ASR API. Not populated yet. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcripts ADD COLUMN segmentsJson TEXT")
            }
        }
        /**
         * v4.2: user-given speaker names per recording (display labels only, not voiceprints; nullable, no backfill)
         * and the capture marks table. Both only add; nothing existing is rewritten.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN speakerNamesJson TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `capture_marks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `offsetMs` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_capture_marks_sessionId` ON `capture_marks` (`sessionId`)")
            }
        }
        private fun createMemoryTables(db: SupportSQLiteDatabase) {
            listOf(
                """CREATE TABLE IF NOT EXISTS recording_sessions (id TEXT NOT NULL PRIMARY KEY,
                    mode TEXT NOT NULL,startedAt INTEGER NOT NULL,endedAt INTEGER,durationMs INTEGER NOT NULL,
                    title TEXT NOT NULL,captureState TEXT NOT NULL,processingState TEXT NOT NULL,
                    sourceRevision INTEGER NOT NULL,sourcePath TEXT,error TEXT)""",
                """CREATE TABLE IF NOT EXISTS transcripts (rowid INTEGER NOT NULL PRIMARY KEY,
                    text TEXT NOT NULL,provenance TEXT NOT NULL,createdAt INTEGER NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS memory_topics (id TEXT NOT NULL PRIMARY KEY,
                    sessionId TEXT NOT NULL,title TEXT NOT NULL,summary TEXT NOT NULL,sourcesJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,revision INTEGER NOT NULL,hidden INTEGER NOT NULL,actionsJson TEXT NOT NULL)""",
                "CREATE INDEX IF NOT EXISTS index_memory_topics_sessionId ON memory_topics(sessionId)",
                "CREATE INDEX IF NOT EXISTS index_memory_topics_createdAt ON memory_topics(createdAt)",
                """CREATE TABLE IF NOT EXISTS derived_notes (sessionId TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,summary TEXT NOT NULL,sourcesJson TEXT NOT NULL,templateId TEXT NOT NULL,
                    sourceRevision INTEGER NOT NULL,createdAt INTEGER NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS daily_digests (date TEXT NOT NULL PRIMARY KEY,zoneId TEXT NOT NULL,
                    title TEXT NOT NULL,content TEXT NOT NULL,sourcesJson TEXT NOT NULL,
                    sourceFingerprint TEXT NOT NULL,createdAt INTEGER NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS action_tasks (id TEXT NOT NULL PRIMARY KEY,sessionId TEXT,
                    text TEXT NOT NULL,state TEXT NOT NULL,sourcesJson TEXT NOT NULL,createdAt INTEGER NOT NULL,
                    whenHint TEXT,sourceDeleted INTEGER NOT NULL)""",
                "CREATE INDEX IF NOT EXISTS index_action_tasks_sessionId ON action_tasks(sessionId)",
                "CREATE INDEX IF NOT EXISTS index_action_tasks_state ON action_tasks(state)",
                """CREATE TABLE IF NOT EXISTS processing_jobs (id TEXT NOT NULL PRIMARY KEY,kind TEXT NOT NULL,
                    subjectId TEXT NOT NULL,state TEXT NOT NULL,attempts INTEGER NOT NULL,error TEXT,
                    owner TEXT,updatedAt INTEGER NOT NULL,resultJson TEXT)""",
                "CREATE INDEX IF NOT EXISTS index_processing_jobs_state ON processing_jobs(state)",
            ).forEach(db::execSQL)
        }

        private fun migrateLegacyContent(db: SupportSQLiteDatabase) {
            val zone = ZoneId.systemDefault()
            val summaryParts = linkedMapOf<String, MutableList<String>>()
            db.query("SELECT * FROM audio_records ORDER BY createdAt,id").use { cursor ->
                fun str(name: String): String? = cursor.getColumnIndex(name).let { i ->
                    if (i < 0 || cursor.isNull(i)) null else cursor.getString(i)
                }
                fun long(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                while (cursor.moveToNext()) {
                    val id = long("id"); val session = str("sessionId")!!
                    val created = long("createdAt"); val duration = long("durationMs")
                    val title = str("title").orEmpty().ifBlank { "旧记录" }
                    val text = str("transcription").orEmpty()
                    val summary = str("summary").orEmpty()
                    if (session.startsWith("lifelog_") || str("tags")?.contains("每日复盘") == true) {
                        val date = session.removePrefix("lifelog_").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                            ?: Instant.ofEpochMilli(created).atZone(zone).toLocalDate().toString()
                        db.execSQL("INSERT OR REPLACE INTO daily_digests VALUES (?,?,?,?,?,?,?)",
                            arrayOf<Any?>(date, zone.id, title, text.ifBlank { summary }, "[]", "legacy", created))
                        continue
                    }
                    val mode = str("recordingMode") ?: if (str("filePath")?.endsWith(".wav") == true) "meeting" else "lifelog"
                    db.execSQL("INSERT OR IGNORE INTO recording_sessions VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any?>(session, mode, created, created + duration, 0L, title, "CLOSED", "READY", 0,
                            if (session.startsWith("import_")) str("filePath") else null, null))
                    db.execSQL("UPDATE recording_sessions SET endedAt=MAX(endedAt,?),durationMs=durationMs+? WHERE id=?",
                        arrayOf<Any?>(created + duration, duration, session))
                    val excerpt=text.take(200)
                    val refs = if (text.isNotBlank()) listOf(EvidenceRef(id, 0, excerpt.length, excerpt)) else emptyList()
                    val refsJson = EvidenceRef.toJson(refs)
                    if (text.isNotBlank()) db.execSQL("INSERT OR IGNORE INTO transcripts VALUES (?,?,?,?)", arrayOf<Any?>(id, text, "LEGACY", created))
                    if (mode != "meeting") db.execSQL("INSERT OR IGNORE INTO memory_topics VALUES (?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any?>("legacy-topic:$id", session, title, summary, refsJson, created, 0, 0, "[]"))
                    if (str("status") == "UPLOADED") {
                        summaryParts.getOrPut(session) { mutableListOf() }.add(summary.ifBlank { text.take(500) })
                    } else {
                        db.execSQL("UPDATE recording_sessions SET processingState='PENDING' WHERE id=?", arrayOf<Any?>(session))
                        db.execSQL("INSERT OR IGNORE INTO processing_jobs VALUES (?,?,?,?,?,?,?,?,?)",
                            arrayOf<Any?>("asr:$id", "ASR", id.toString(), "PENDING", 0, null, null, created, null))
                    }
                    ActionItemModel.fromJsonString(str("actionItems")).forEachIndexed { index, action ->
                        val actionRef = action.quote?.let { EvidenceRef.locate(id, text, it) }
                        db.execSQL("INSERT OR IGNORE INTO action_tasks VALUES (?,?,?,?,?,?,?,?)",
                            arrayOf<Any?>("legacy-task:$id:$index", session, action.text,
                                if (action.isDone) TaskState.DONE else TaskState.ACTIVE,
                                EvidenceRef.toJson(actionRef?.let(::listOf) ?: emptyList()), created, action.whenHint, 0))
                    }
                }
            }
            summaryParts.forEach { (session, parts) ->
                db.execSQL("""INSERT OR IGNORE INTO derived_notes
                    SELECT id,title,?,'[]','legacy',0,startedAt FROM recording_sessions WHERE id=?""",
                    arrayOf<Any?>(parts.filter(String::isNotBlank).joinToString("\n\n"), session))
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: run {
                backupBeforeUpgrade(context.applicationContext)
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shuolesa_db")
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build().also { instance = it }
            }
        }

        /**
         * Copies the database before any schema upgrade, into files/backups/pre-v<target>/.
         * v4.1.1: this used to stop at schema 6, so the 6 -> 7 upgrade ran without a copy.
         */
        private fun backupBeforeUpgrade(context: Context) {
            val database = context.getDatabasePath("shuolesa_db")
            if (!database.exists()) return
            val version = SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            if (version >= DB_SCHEMA_VERSION) return
            val backup = File(context.filesDir, "backups/pre-v$DB_SCHEMA_VERSION")
            if (File(backup, "complete").exists()) return
            check(backup.mkdirs() || backup.isDirectory) { "无法创建升级前数据备份，升级已停止" }
            listOf("", "-wal", "-shm").forEach { suffix ->
                File(database.path + suffix).takeIf(File::exists)?.copyTo(File(backup, "shuolesa_db$suffix"),overwrite=true)
            }
            File(backup,"complete").writeText("schema=$version\n")
        }
    }
}
