package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.data.repository.MemoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MemoryRepositoryTest {
    private lateinit var db:AppDatabase
    private lateinit var repo:MemoryRepository
    private lateinit var audio:File
    private var chunkId=0L
    @Before fun prepare()=runBlocking {
        val context:Context=ApplicationProvider.getApplicationContext()
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();repo=MemoryRepository(db,context)
        audio=File(context.filesDir,"test-original.wav").apply {writeText("audio")}
        repo.createSession(RecordingSessionEntity("s","lifelog",1))
        chunkId=repo.registerChunk(AudioRecordEntity(sessionId="s",chunkIndex=0,filePath=audio.path,durationMs=1000,fileSizeBytes=80,createdAt=1))
        repo.saveTranscript(repo.audio.getById(chunkId)!!,"周五交付第一版。先确认需求。")
    }
    @After fun close() {db.close();audio.delete()}
    private fun draft(text:String="交付第一版")=SessionDraft("主题","纪要",listOf(TopicDraft("交付","周五交付",listOf(ref()),listOf(ActionDraft(text,listOf(ref()))))))
    private fun ref()=EvidenceRef.locate(chunkId,"周五交付第一版。先确认需求。","周五交付第一版。")!!
    @Test fun rawTextIsImmutableAndChineseSearchIsComplete()=runBlocking {
        repo.saveTranscript(repo.audio.getById(chunkId)!!,"试图覆盖原稿")
        assertEquals("周五交付第一版。先确认需求。",repo.dao.transcript(chunkId)!!.text)
        assertEquals(chunkId,repo.search("确认需求").single().chunkId)
        assertEquals(chunkId,repo.dao.searchFts("\"周五交付第一版\"",0,Long.MAX_VALUE,"all").single().chunkId)
    }
    @Test fun completedAndIgnoredTasksSurviveRegeneration()=runBlocking {
        repo.saveDerived(repo.dao.session("s")!!,draft())
        val task=repo.dao.tasksForSession("s").single();assertEquals(TaskState.CANDIDATE,task.state)
        repo.dao.transitionTask(task.id,TaskState.CANDIDATE,TaskState.ACTIVE)
        repo.dao.transitionTask(task.id,TaskState.ACTIVE,TaskState.DONE)
        repo.saveDerived(repo.dao.session("s")!!,draft("交付新版内容"))
        assertEquals(TaskState.DONE,repo.dao.tasksForSession("s").single().state)
    }
    @Test fun hiddenTopicsDoNotReappearOnSameSourceRegeneration()=runBlocking {
        repo.saveDerived(repo.dao.session("s")!!,draft());val topic=repo.dao.topics("s").single()
        repo.dao.setTopicHidden(topic.id,true);repo.saveDerived(repo.dao.session("s")!!,draft())
        assertTrue(repo.dao.topic(topic.id)!!.hidden)
    }
    @Test fun atomicJobClaimsAndTaskTransitionsHaveOnlyOneWinner()=runBlocking {
        repo.dao.insertJob(ProcessingJobEntity("j","ASR",chunkId.toString()))
        val claims=(0 until 20).map {i->async(Dispatchers.IO) {repo.dao.claimJob("j","owner-$i",i.toLong())}}.awaitAll()
        assertEquals(1,claims.sum())
        repo.saveDerived(repo.dao.session("s")!!,draft());val task=repo.dao.tasksForSession("s").single()
        val transitions=(0 until 20).map {async(Dispatchers.IO) {repo.dao.transitionTask(task.id,TaskState.CANDIDATE,TaskState.ACTIVE)}}.awaitAll()
        assertEquals(1,transitions.sum())
    }
    @Test fun sessionDeletionKeepsConfirmedTasksAndRejectsLateResponses()=runBlocking {
        val chunk=repo.audio.getById(chunkId)!!;val session=repo.dao.session("s")!!
        repo.saveDerived(session,draft());val task=repo.dao.tasksForSession("s").single()
        repo.dao.transitionTask(task.id,TaskState.CANDIDATE,TaskState.ACTIVE)
        repo.deleteSession("s");repo.saveTranscript(chunk,"迟到的响应");repo.saveDerived(session,draft())
        assertNull(repo.dao.session("s"));assertNull(repo.dao.transcript(chunkId));assertFalse(audio.exists())
        val kept=repo.dao.observeTasks().first().single()
        assertNull(kept.sessionId);assertTrue(kept.sourceDeleted);assertEquals(TaskState.ACTIVE,kept.state)
    }
    @Test fun invalidReferencesCannotPublishAndAudioCleanupKeepsText()=runBlocking {
        val bad=SessionDraft("bad","bad",listOf(TopicDraft("bad","bad",listOf(ref().copy(quote="捏造内容")))))
        try {repo.saveDerived(repo.dao.session("s")!!,bad);fail("invalid source accepted")} catch(_:IllegalArgumentException) {}
        repo.cleanAudio("s");assertNotNull(repo.dao.transcript(chunkId));assertNotNull(repo.dao.session("s"));assertFalse(audio.exists())
    }
}
