package com.example.shuolesa

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.db.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import org.json.JSONObject
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class DatabaseMigrationTest {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private fun fixture(version:Int,sqlDefault:Boolean=false,broken:Boolean=false):Pair<String,File> {
        val name="legacy-${UUID.randomUUID()}";val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val audio=File(context.filesDir,"original-${UUID.randomUUID()}.wav").apply {writeText("original audio sentinel")}
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            val extras=(if(version>=4) ",recordingMode TEXT${if(sqlDefault) " DEFAULT 'lifelog'" else ""},audioFormat TEXT" else "")+(if(version>=5 && !broken) ",segmentsJson TEXT" else "")
            db.execSQL("""CREATE TABLE audio_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,sessionId TEXT NOT NULL,chunkIndex INTEGER NOT NULL,filePath TEXT NOT NULL,durationMs INTEGER NOT NULL,fileSizeBytes INTEGER NOT NULL,createdAt INTEGER NOT NULL,status TEXT NOT NULL,uploadedAt INTEGER,retryCount INTEGER NOT NULL,title TEXT,summary TEXT,actionItems TEXT,tags TEXT,transcription TEXT,agentResult TEXT$extras)""")
            val extraNames=if(version>=4) ",recordingMode,audioFormat" else ""
            val extraValues=if(version>=4) ",'meeting','wav'" else ""
            db.execSQL("""INSERT INTO audio_records (id,sessionId,chunkIndex,filePath,durationMs,fileSizeBytes,createdAt,status,retryCount,title,summary,actionItems,tags,transcription$extraNames) VALUES (42,'session-old',0,?,30000,?,1791936000000,'UPLOADED',0,'旧会议','旧摘要','[{"text":"交付第一版","done":true,"quote":"周五交付第一版。"},{"text":"历史事项","done":false}]','["会议"]','周五交付第一版。'$extraValues)""",arrayOf<Any?>(audio.path,audio.length()))
            db.execSQL("""INSERT INTO audio_records (id,sessionId,chunkIndex,filePath,durationMs,fileSizeBytes,createdAt,status,retryCount,title,summary,tags) VALUES (43,'pending-old',0,?,4000,20,1791936000000,'UPLOADING',0,'待处理',NULL,'[]')""",arrayOf<Any?>(audio.path))
            db.execSQL("""INSERT INTO audio_records (id,sessionId,chunkIndex,filePath,durationMs,fileSizeBytes,createdAt,status,retryCount,title,summary,tags) VALUES (44,'lifelog_2026-10-14',0,'',0,0,1791936000000,'UPLOADED',0,'每日复盘','旧复盘正文','["每日复盘"]')""")
            db.version=version
        }
        return name to audio
    }
    private fun open(name:String)=Room.databaseBuilder(context,AppDatabase::class.java,name)
        .addMigrations(AppDatabase.MIGRATION_3_4,AppDatabase.MIGRATION_4_5,AppDatabase.MIGRATION_5_6,AppDatabase.MIGRATION_6_7,AppDatabase.MIGRATION_7_8).build()
    @Test fun freshV5AndMigratedDefaultsBothPreserveRowsAndTasks()=runBlocking {
        listOf(3 to false,4 to true,5 to false,5 to true).forEach { (version,default)->
            val(name,audio)=fixture(version,default);val db=open(name)
            try {
                db.openHelper.writableDatabase
                val dao=db.memoryDao()
                assertEquals(3,db.audioRecordDao().getAll().size)
                assertEquals(audio.path,db.audioRecordDao().getById(42)!!.filePath)
                assertEquals("original audio sentinel",audio.readText())
                assertEquals("LEGACY",dao.transcript(42)!!.provenance)
                assertEquals("周五交付第一版。",dao.transcript(42)!!.text)
                val tasks=dao.tasksForSession("session-old")
                assertEquals(setOf("ACTIVE","DONE"),tasks.map {it.state}.toSet())
                assertEquals("[]",tasks.find {it.text=="历史事项"}!!.sourcesJson)
                assertEquals("PENDING",db.audioRecordDao().getById(43)!!.status)
                assertEquals("PENDING",dao.job("asr:43")!!.state)
                assertEquals(1,dao.observeDigests().first().size)
                assertNull(dao.session("lifelog_2026-10-14"))
                assertEquals(42,dao.searchSubstring("交付",0,Long.MAX_VALUE,"all").single().chunkId)
            } finally {db.close();context.deleteDatabase(name);audio.delete()}
        }
    }
    /** v4.1-a: schema 6 (exact V4 createSql from 6.json) -> 7 keeps rows, adds nullable transcripts.segmentsJson. */
    @Test fun v6ToV7AddsNullableSegmentsJsonAndKeepsData()=runBlocking {
        val schema=listOf("schemas","app/schemas").map { File(it,"com.example.shuolesa.data.db.AppDatabase/6.json") }.first { it.exists() }
        val database=JSONObject(schema.readText()).getJSONObject("database")
        val name="v6-${UUID.randomUUID()}";val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            val entities=database.getJSONArray("entities")
            for(i in 0 until entities.length()) { val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                entity.optJSONArray("indices")?.let { idx -> for(j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table)) }
            }
            val setup=database.getJSONArray("setupQueries");for(i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO audio_records (id,sessionId,chunkIndex,filePath,durationMs,fileSizeBytes,createdAt,status,retryCount,recordingMode) VALUES (7,'s6',0,'/x.wav',1000,10,1791936000000,'TRANSCRIBED',0,'meeting')")
            db.execSQL("INSERT INTO recording_sessions VALUES ('s6','meeting',1791936000000,NULL,1000,'会议录音','CLOSED','READY',0,NULL,NULL)")
            db.execSQL("INSERT INTO transcripts (rowid,text,provenance,createdAt) VALUES (7,'下周三评审接口。','ASR',1791936000000)")
            db.execSQL("INSERT INTO transcripts_fts(transcripts_fts) VALUES('rebuild')")
            db.version=6
        }
        val room=open(name)
        try {
            room.openHelper.writableDatabase.query("PRAGMA table_info(transcripts)").use { c ->
                var found=false;while(c.moveToNext()) if(c.getString(c.getColumnIndexOrThrow("name"))=="segmentsJson") {found=true;assertEquals(0,c.getInt(c.getColumnIndexOrThrow("notnull")))}
                assertTrue(found)
            }
            val t=room.memoryDao().transcript(7)!!
            assertEquals("下周三评审接口。",t.text);assertNull(t.segmentsJson)
            assertEquals(7L,room.memoryDao().searchSubstring("评审",0,Long.MAX_VALUE,"all").single().chunkId)
            assertEquals("s6",room.memoryDao().session("s6")!!.id)
        } finally {room.close();context.deleteDatabase(name)}
    }
    /** v4.2: schema 7 (createSql from 7.json) -> 8 keeps rows and timestamps, adds nullable recording_sessions.speakerNamesJson and capture_marks. */
    @Test fun v7ToV8AddsNullableSpeakerNamesAndKeepsData()=runBlocking {
        val schema=listOf("schemas","app/schemas").map { File(it,"com.example.shuolesa.data.db.AppDatabase/7.json") }.first { it.exists() }
        val database=JSONObject(schema.readText()).getJSONObject("database")
        val name="v7-${UUID.randomUUID()}";val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            val entities=database.getJSONArray("entities")
            for(i in 0 until entities.length()) { val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                entity.optJSONArray("indices")?.let { idx -> for(j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table)) }
            }
            val setup=database.getJSONArray("setupQueries");for(i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO audio_records (id,sessionId,chunkIndex,filePath,durationMs,fileSizeBytes,createdAt,status,retryCount,recordingMode) VALUES (8,'s7',0,'/x.wav',1000,10,1791936000000,'UPLOADED',0,'meeting')")
            db.execSQL("INSERT INTO recording_sessions VALUES ('s7','meeting',1791936000000,NULL,1000,'会议录音','CLOSED','READY',0,NULL,NULL)")
            db.execSQL("INSERT INTO transcripts (rowid,text,provenance,createdAt,segmentsJson) VALUES (8,'下周三评审接口。','ASR',1791936000000,'[{\"text\":\"下周三评审接口。\",\"start\":0}]')")
            db.execSQL("INSERT INTO transcripts_fts(transcripts_fts) VALUES('rebuild')")
            db.version=7
        }
        val room=open(name)
        try {
            room.openHelper.writableDatabase.query("PRAGMA table_info(recording_sessions)").use { c ->
                var found=false;while(c.moveToNext()) if(c.getString(c.getColumnIndexOrThrow("name"))=="speakerNamesJson") {found=true;assertEquals(0,c.getInt(c.getColumnIndexOrThrow("notnull")))}
                assertTrue(found)
            }
            assertNull(room.memoryDao().session("s7")!!.speakerNamesJson)
            assertNotNull(room.memoryDao().transcript(8)!!.segmentsJson)
            // v4.2: capture marks table added by the same migration
            room.memoryDao().insertMark(com.example.shuolesa.data.db.CaptureMarkEntity(sessionId="s7",offsetMs=52_000,createdAt=1))
            room.memoryDao().insertMark(com.example.shuolesa.data.db.CaptureMarkEntity(sessionId="s7",offsetMs=6_000,createdAt=2))
            assertEquals(listOf(6_000L,52_000L),room.memoryDao().marksForSession("s7"))
            room.memoryDao().setSpeakerNames("s7","{\"1\":\"张三\"}")
            assertEquals("{\"1\":\"张三\"}",room.memoryDao().session("s7")!!.speakerNamesJson)
        } finally {room.close();context.deleteDatabase(name)}
    }
    @Test fun unknownV5SchemaFailsWithoutDestroyingOriginal() {
        val(name,audio)=fixture(5,broken=true);val db=open(name)
        try {
            assertThrows(Exception::class.java) {db.openHelper.writableDatabase}
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use {old ->
                assertEquals(5,old.version);old.rawQuery("SELECT COUNT(*) FROM audio_records",null).use {c->c.moveToFirst();assertEquals(3,c.getInt(0))}
            }
            assertEquals("original audio sentinel",audio.readText())
        } finally {db.close();context.deleteDatabase(name);audio.delete()}
    }
}
