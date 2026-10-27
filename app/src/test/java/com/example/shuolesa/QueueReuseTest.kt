package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v4.1.1: digest reuse of MAP results, one-query timestamp slots, and the schema constant. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class QueueReuseTest {
    private lateinit var db:AppDatabase
    @Before fun open() { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun schemaConstantMatchesRoomVersion() { assertEquals(DB_SCHEMA_VERSION,db.openHelper.writableDatabase.version) }

    @Test fun digestReusesTheLatestCompletedMapResult()=runBlocking {
        val dao=db.memoryDao()
        dao.insertJob(ProcessingJobEntity("map:5:0","MAP","5",state="COMPLETE",updatedAt=1,resultJson="[\"old\"]"))
        dao.insertJob(ProcessingJobEntity("map:5:1","MAP","5",state="COMPLETE",updatedAt=2,resultJson="[\"new\"]"))
        dao.insertJob(ProcessingJobEntity("map:5:2","MAP","5",state="PENDING",updatedAt=3))
        dao.insertJob(ProcessingJobEntity("asr:5","ASR","5",state="COMPLETE",updatedAt=4,resultJson="[\"asr\"]"))
        assertEquals("[\"new\"]",dao.latestMapResult("5"))
        assertNull(dao.latestMapResult("6"))
    }

    @Test fun segmentsForSessionReadsOnlyThatSession()=runBlocking {
        val audio=db.audioRecordDao();val dao=db.memoryDao()
        dao.insertSession(RecordingSessionEntity("s","lifelog",1));dao.insertSession(RecordingSessionEntity("o","lifelog",1))
        val a=audio.insert(AudioRecordEntity(sessionId="s",chunkIndex=0,filePath="/a",durationMs=1,fileSizeBytes=1,createdAt=1))
        val b=audio.insert(AudioRecordEntity(sessionId="o",chunkIndex=0,filePath="/b",durationMs=1,fileSizeBytes=1,createdAt=1))
        dao.insertTranscript(TranscriptEntity(a,"原话","ASR",1,segmentsJson="[1]"))
        dao.insertTranscript(TranscriptEntity(b,"别的","ASR",1))
        assertEquals(listOf(TranscriptSegments(a,"[1]")),dao.segmentsForSession("s"))
    }
}
