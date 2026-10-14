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
        .addMigrations(AppDatabase.MIGRATION_3_4,AppDatabase.MIGRATION_4_5,AppDatabase.MIGRATION_5_6).build()
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
