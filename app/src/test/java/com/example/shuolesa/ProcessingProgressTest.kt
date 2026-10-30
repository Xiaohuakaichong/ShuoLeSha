package com.example.shuolesa

import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.db.ProcessingJobEntity
import com.example.shuolesa.data.db.RecordingSessionEntity
import com.example.shuolesa.network.ProcessingReason
import com.example.shuolesa.ui.logic.ProgressLogic
import com.example.shuolesa.ui.logic.StepState
import org.junit.Assert.*
import org.junit.Test

/** v5.2: the step-by-step progress shown after recording, built only from existing rows. */
class ProcessingProgressTest {
    private fun session(state: String = "TRANSCRIBING", capture: String = "CLOSED", source: String? = null, error: String? = null) =
        RecordingSessionEntity("s", "lifelog", 1_000, captureState = capture, processingState = state, sourcePath = source, error = error)
    private fun chunk(id: Long, status: String = "PENDING") =
        AudioRecordEntity(id = id, sessionId = "s", chunkIndex = id.toInt(), filePath = "/x/$id", durationMs = 300_000, fileSizeBytes = 10, createdAt = id * 300_000, status = status)
    private fun job(id: String, state: String, attempts: Int = 0, error: String? = null) =
        ProcessingJobEntity(id, id.substringBefore(':').uppercase(), id.substringAfter(':').substringBefore(':'), state, attempts, error, updatedAt = 42)
    private fun progress(session: RecordingSessionEntity, chunks: List<AudioRecordEntity>, transcribed: Set<Long>, jobs: List<ProcessingJobEntity>, consented: Boolean = true) =
        ProgressLogic.of(session, chunks, transcribed, jobs.associateBy { it.id }::get, consented)

    @Test fun transcribingShowsWhichSegmentAndWhenItStarted() {
        val p = progress(session(), listOf(chunk(1), chunk(2), chunk(3)), setOf(1L),
            listOf(job("asr:1", "COMPLETE"), job("asr:2", "RUNNING"), job("asr:3", "PENDING")))!!
        assertEquals("正在转写 · 第 2/3 段", p.headline)
        assertEquals(42L, p.runningSince)
        val asr = p.steps.single { it.key == "asr" }
        assertEquals(StepState.RUNNING, asr.state); assertEquals(1, asr.done); assertEquals(3, asr.total)
        assertEquals(StepState.WAITING, p.steps.single { it.key == "map" }.state)
        assertTrue(p.fraction > 0.2f && p.fraction < 0.6f)
        assertEquals(listOf(StepState.DONE, StepState.RUNNING, StepState.WAITING), p.segments.map { it.transcribe })
        assertFalse(p.finished)
    }

    @Test fun organisingAndSummarisingFollowTranscription() {
        val chunks = listOf(chunk(1, "UPLOADED"), chunk(2, "UPLOADED"))
        val mapping = progress(session("DERIVING"), chunks, setOf(1L, 2L),
            listOf(job("map:1:0", "COMPLETE"), job("map:2:0", "RUNNING"), job("derive:s:0", "WAITING_DEPENDENCY")))!!
        assertEquals("正在分段整理 · 第 2/2 段", mapping.headline)
        assertEquals(StepState.DONE, mapping.steps.single { it.key == "asr" }.state)
        val summarising = progress(session("DERIVING"), chunks, setOf(1L, 2L),
            listOf(job("map:1:0", "COMPLETE"), job("map:2:0", "COMPLETE"), job("derive:s:0", "RUNNING")))!!
        assertEquals("正在汇总要点和待办", summarising.headline)
        assertTrue(summarising.fraction > mapping.fraction)
    }

    @Test fun readyIsFinishedAtOneHundredPercent() {
        val p = progress(session("READY"), listOf(chunk(1, "UPLOADED")), setOf(1L), emptyList())!!
        assertTrue(p.finished); assertEquals(1f, p.fraction, 0f); assertEquals("整理完成", p.headline)
        assertTrue(p.steps.all { it.state == StepState.DONE })
    }

    @Test fun aMissingKeyPausesWithTheReason() {
        val p = progress(session("WAITING_CONFIG"), listOf(chunk(1)), emptySet(),
            listOf(job("asr:1", "WAITING_CONFIG", error = ProcessingReason.KEY_MISSING.encode("请先填写服务密钥"))))!!
        assertEquals(ProcessingReason.KEY_MISSING, p.reason)
        assertEquals("已暂停 · 还没填服务密钥", p.headline)
        assertEquals("请先填写服务密钥", p.detail)
        assertEquals(StepState.BLOCKED, p.steps.single { it.key == "asr" }.state)
    }

    @Test fun withoutConsentItWaitsForTheUser() {
        val p = progress(session(), listOf(chunk(1)), emptySet(), listOf(job("asr:1", "PENDING")), consented = false)!!
        assertEquals(ProcessingReason.AWAITING_CONSENT, p.reason)
        assertEquals("等你确认数据外发", p.headline)
    }

    @Test fun fiveFailuresIsAFailure() {
        val p = progress(session("FAILED"), listOf(chunk(1)), emptySet(), listOf(job("asr:1", "FAILED", attempts = 5, error = "[FAILED] 转写响应没有文字字段")))!!
        assertTrue(p.failed); assertEquals("处理失败", p.headline); assertEquals("转写响应没有文字字段", p.detail)
        val retrying = progress(session("FAILED"), listOf(chunk(1)), emptySet(), listOf(job("asr:1", "FAILED", attempts = 2)))!!
        assertFalse("retried automatically before the fifth attempt", retrying.failed)
    }

    @Test fun aMeetingTranscribedInOnePassShowsAsOneRunningStep() {
        val p = progress(session(), listOf(chunk(1, "UPLOADING"), chunk(2, "UPLOADING")), emptySet(),
            listOf(job("asr:1", "RUNNING"), job("asr:2", "PENDING")))!!
        assertEquals("正在整场转写 · 共 2 段", p.headline)
    }

    @Test fun importsStartWithDecoding() {
        val p = progress(session(source = "/x/original.m4a"), listOf(chunk(1, "PENDING")), emptySet(), listOf(job("import:s", "RUNNING")))!!
        assertEquals("import", p.steps.first().key)
        assertEquals("正在解码导入的音频 · 已切出 1 段", p.headline)
    }

    @Test fun whileRecordingItSaysSoAndNothingIsShownForEmptyAudio() {
        val recording = progress(session(capture = "RECORDING"), listOf(chunk(1, "UPLOADED")), setOf(1L), listOf(job("asr:1", "COMPLETE")))!!
        assertTrue(recording.recording); assertEquals("录音中 · 已转写 1/1 段", recording.headline)
        assertNull(progress(session("EMPTY"), emptyList(), emptySet(), emptyList()))
    }
}
