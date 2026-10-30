package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.repository.MemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** v5.2: 删除整条记录 works while the record is still being processed, and nothing written late brings it back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class DeleteSessionTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: MemoryRepository
    private lateinit var context: Context
    private lateinit var audio: File
    private var chunkId = 0L

    @Before fun prepare() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build(); repo = MemoryRepository(db, context)
        audio = File(context.filesDir, "delete-test.wav").apply { writeText("audio") }
        repo.createSession(RecordingSessionEntity("s", "lifelog", 1, captureState = "CLOSED", processingState = "TRANSCRIBING"))
        chunkId = repo.registerChunk(AudioRecordEntity(sessionId = "s", chunkIndex = 0, filePath = audio.path, durationMs = 1000, fileSizeBytes = 80, createdAt = 1))
        repo.queueAsr(chunkId)
        Unit
    }
    @After fun close() { db.close(); audio.delete() }

    @Test fun deletesWhileItsTranscriptionIsRunning() = runBlocking {
        assertEquals(1, repo.dao.claimJob("asr:$chunkId", "runner", 2))
        repo.deleteSession("s")
        assertNull(repo.dao.session("s"))
        assertNull(repo.audio.getById(chunkId))
        assertNull(repo.dao.job("asr:$chunkId"))
        assertFalse("the audio file is gone", audio.exists())
    }

    @Test fun lateResultsOfACancelledJobDoNotBringItBack() = runBlocking {
        val chunk = repo.audio.getById(chunkId)!!
        repo.deleteSession("s")
        repo.saveTranscript(chunk, "转写在删除之后才返回")
        assertNull(repo.dao.transcript(chunkId))
        repo.queueDerive("s")
        assertNull(repo.dao.session("s"))
        val refused = runCatching { repo.registerChunk(AudioRecordEntity(sessionId = "s", chunkIndex = 1, filePath = "x", durationMs = 0, fileSizeBytes = 0, createdAt = 1)) }
        assertTrue("an import still cutting 片段 cannot add one", refused.isFailure)
    }

    @Test fun aRecordingInProgressIsNotDeleted() = runBlocking {
        repo.dao.updateCapture("s", "RECORDING", null, 1000)
        assertTrue(runCatching { repo.deleteSession("s") }.isFailure)
        assertNotNull(repo.dao.session("s"))
    }

    @Test fun cleaningAudioKeepsWhatIsNotTranscribedYet() = runBlocking {
        val refused = runCatching { repo.cleanAudio("s") }
        assertTrue(refused.isFailure); assertTrue(audio.exists())
        repo.saveTranscript(repo.audio.getById(chunkId)!!, "已经转写好了")
        repo.cleanAudio("s")
        assertFalse(audio.exists())
        assertNotNull("the text stays", repo.dao.transcript(chunkId))
    }
}
