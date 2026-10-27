package com.example.shuolesa

import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.repository.MemorySnapshot
import com.example.shuolesa.network.AiClient
import com.example.shuolesa.network.AiException
import com.example.shuolesa.ui.cards
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/** v4.1.1: the indexed cards() keeps the list semantics of the former nested scans. */
class MemoryCardsTest {
    private fun ref(chunk:Long,start:Int)=EvidenceRef(chunk,start,start+2,"原话")
    private fun chunk(id:Long,session:String)=AudioRecordEntity(id=id,sessionId=session,chunkIndex=0,filePath="/f/$id",
        durationMs=1000,fileSizeBytes=10,createdAt=id,status="UPLOADED")

    @Test fun topicCardsCountOnlyTheirOwnCandidatesAndLegacyIsPerSession() {
        val lifelog=RecordingSessionEntity("life","lifelog",100,durationMs=5000,processingState="READY")
        val meeting=RecordingSessionEntity("meet","meeting",50,processingState="READY",title="会议录音")
        val snap=MemorySnapshot(
            sessions=listOf(lifelog,meeting),
            chunks=listOf(chunk(1,"life"),chunk(2,"meet")),
            transcripts=listOf(TranscriptMeta(1,"ASR",1,10),TranscriptMeta(2,"LEGACY",2,10)),
            topics=listOf(
                MemoryTopicEntity("t-new","life","新话题","摘要",EvidenceRef.toJson(listOf(ref(1,0))),200,revision=1),
                MemoryTopicEntity("t-old","life","旧版话题","摘要",EvidenceRef.toJson(listOf(ref(1,4))),150,revision=0)),
            notes=listOf(DerivedNoteEntity("life","整体","纪要",EvidenceRef.toJson(listOf(ref(1,0))),"general",1,300),
                DerivedNoteEntity("meet","周会","会议纪要","[]","general",0,60)),
            tasks=listOf(
                TaskEntity("a","life","交付",TaskState.CANDIDATE,EvidenceRef.toJson(listOf(ref(1,0))),1),
                TaskEntity("b","life","别的",TaskState.CANDIDATE,EvidenceRef.toJson(listOf(ref(1,8))),1),
                TaskEntity("c","life","已确认",TaskState.ACTIVE,EvidenceRef.toJson(listOf(ref(1,0))),1),
                TaskEntity("d","meet","会议待办",TaskState.CANDIDATE,"[]",1),
                TaskEntity("e",null,"来源已删除",TaskState.CANDIDATE,"[]",1)),
        )
        val cards=snap.cards()
        assertEquals(listOf("t-new","meet"),cards.map { it.key })
        val topic=cards[0]
        assertEquals("新话题",topic.title);assertEquals(1,topic.candidates);assertFalse(topic.legacy)
        val session=cards[1]
        assertNull(session.topicId);assertEquals("周会",session.title);assertEquals(1,session.candidates);assertTrue(session.legacy)
    }

    @Test fun readyLifelogWithoutVisibleTopicsIsHiddenButUnprocessedOneShows() {
        val snap=MemorySnapshot(
            sessions=listOf(RecordingSessionEntity("h","lifelog",10,processingState="READY"),
                RecordingSessionEntity("p","lifelog",20,processingState="TRANSCRIBING")),
            notes=listOf(DerivedNoteEntity("h","标题","纪要",EvidenceRef.toJson(listOf(ref(1,0))),"general",0,1)))
        assertEquals(listOf("p"),snap.cards().map { it.key })
    }
}

/** v4.1.1: an empty topic list (silence, a few words) is a result, not a MAP failure. */
class MapTopicsTest {
    private lateinit var server:MockWebServer
    @Before fun start() {server=MockWebServer();server.start(InetAddress.getByName("127.0.0.1"),0)}
    @After fun stop() {server.shutdown()}
    private fun config()=AiSettings("custom",server.url("/v1").newBuilder().host("127.0.0.1").build().toString().trimEnd('/'),
        "test-only-secret","asr","llm","")
    private fun reply(content:String)=MockResponse().setBody(JSONObject().put("choices",JSONArray().put(
        JSONObject().put("message",JSONObject().put("content",content)))).toString())
    private val source=SourceText(7,"嗯，好的。",1)

    @Test fun emptyTopicListIsSilenceNotFailure()=runBlocking {
        server.enqueue(reply("{\"topics\":[]}"))
        assertTrue(AiClient().mapTopics(config(),source,"lifelog").isEmpty())
    }
    @Test fun topicsWithoutVerifiableQuotesStillFail()=runBlocking {
        server.enqueue(reply("{\"topics\":[{\"title\":\"t\",\"summary\":\"s\",\"sources\":[{\"chunk_id\":7,\"quote\":\"没说过的话\"}]}]}"))
        try { AiClient().mapTopics(config(),source,"lifelog");fail("unverifiable topics accepted") }
        catch(e:AiException) { assertEquals(-1,e.code) }
    }
    @Test fun shortSourceQuotedWholeIsKept()=runBlocking {
        server.enqueue(reply("{\"topics\":[{\"title\":\"t\",\"summary\":\"s\",\"sources\":[{\"chunk_id\":7,\"quote\":\"嗯，好的。\"}]}]}"))
        val topics=AiClient().mapTopics(config(),source,"lifelog")
        assertEquals(EvidenceRef(7,0,5,"嗯，好的。"),topics.single().sources.single())
    }
}
