package com.example.shuolesa.ui.logic

import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.db.ProcessingJobEntity
import com.example.shuolesa.data.db.RecordingSessionEntity
import com.example.shuolesa.network.ProcessingReason

/*
 * v5.2: what happens to a record after the microphone stops, step by step. Built only from rows that already exist
 * (session, 片段, 原话, jobs), so the detail page, the home card and the tests all read the same thing.
 * Job ids: import:<session>, asr:<chunk>, map:<chunk>:<revision>, derive:<session>:<revision>.
 */

enum class StepState { WAITING, RUNNING, DONE, BLOCKED, FAILED }
data class ProgressStep(val key: String, val title: String, val done: Int, val total: Int, val state: StepState)
/** [start]/[end]: wall-clock times for recordings, offsets into the file for imports ([imported]). */
data class SegmentProgress(val chunkId: Long, val index: Int, val start: Long, val end: Long, val imported: Boolean,
    val transcribe: StepState, val organise: StepState)
data class ProcessingProgress(val steps: List<ProgressStep>, val segments: List<SegmentProgress>, val fraction: Float,
    val headline: String, val detail: String?, val runningSince: Long?, val reason: ProcessingReason?,
    val recording: Boolean, val finished: Boolean) {
    val failed get() = reason == ProcessingReason.FAILED
}

object ProgressLogic {
    private val ACTIVE = setOf("STARTING", "RECORDING", "PAUSED", "STOPPING")
    private val NO_AUDIO = setOf("EMPTY", "SOURCE", "WRITING")

    fun stateOf(job: ProcessingJobEntity?): StepState = when (job?.state) {
        null -> StepState.WAITING
        "COMPLETE" -> StepState.DONE
        "RUNNING" -> StepState.RUNNING
        "WAITING_CONFIG", "WAITING_NETWORK" -> StepState.BLOCKED
        // a failure is retried automatically until the fifth attempt
        "FAILED" -> if (job.attempts >= 5) StepState.FAILED else StepState.WAITING
        else -> StepState.WAITING // PENDING, WAITING_DEPENDENCY
    }

    private fun summarise(states: List<StepState>, done: Boolean): StepState = when {
        done -> StepState.DONE
        StepState.RUNNING in states -> StepState.RUNNING
        StepState.FAILED in states -> StepState.FAILED
        StepState.BLOCKED in states -> StepState.BLOCKED
        else -> StepState.WAITING
    }

    /**
     * [chunks] = this session's 片段 (any status), [transcribed] = chunk ids that have 原话, [job] = lookup by job id.
     * Null when there is nothing to process (no audio was recorded).
     */
    fun of(session: RecordingSessionEntity, chunks: List<AudioRecordEntity>, transcribed: Set<Long>,
           job: (String) -> ProcessingJobEntity?, consented: Boolean): ProcessingProgress? {
        if (session.processingState == "EMPTY") return null
        val ready = session.processingState == "READY"
        val recording = session.captureState in ACTIVE
        val rev = session.sourceRevision
        val imported = session.sourcePath != null
        val audio = chunks.filter { it.sessionId == session.id && it.status !in NO_AUDIO }.sortedBy { it.chunkIndex }
        val importJob = if (imported) job("import:${session.id}") else null
        val segments = audio.mapIndexed { i, c ->
            val asrJob = job("asr:${c.id}")
            var asr = if (ready || c.id in transcribed) StepState.DONE else stateOf(asrJob)
            // a meeting is transcribed in one pass: every 片段 is marked UPLOADING while the first one's job runs
            if (asr == StepState.WAITING && c.status == "UPLOADING") asr = StepState.RUNNING
            val map = if (ready) StepState.DONE else stateOf(job("map:${c.id}:$rev"))
            val start = if (imported) c.sourceStartMs ?: 0L else c.createdAt
            SegmentProgress(c.id, i, start, start + c.durationMs, imported, asr, map)
        }
        val total = segments.size
        val steps = ArrayList<ProgressStep>()
        val importState = when { !imported || ready || importJob == null -> StepState.DONE; else -> stateOf(importJob) }
        if (imported) steps += ProgressStep("import", "解码导入的音频", if (importState == StepState.DONE) 1 else 0, 1, importState)
        val transcribedCount = segments.count { it.transcribe == StepState.DONE }
        val transcribeState = summarise(segments.map { it.transcribe }, ready || (total > 0 && transcribedCount == total && importState == StepState.DONE))
        steps += ProgressStep("asr", "转写原话", transcribedCount, total, if (recording && transcribeState == StepState.DONE) StepState.WAITING else transcribeState)
        val organisedCount = segments.count { it.organise == StepState.DONE }
        val organiseState = if (transcribeState != StepState.DONE || recording) StepState.WAITING
            else summarise(segments.map { it.organise }, ready || (total > 0 && organisedCount == total))
        steps += ProgressStep("map", "分段整理", organisedCount, total, organiseState)
        val deriveJob = job("derive:${session.id}:$rev")
        val deriveState = when { ready -> StepState.DONE; organiseState != StepState.DONE -> StepState.WAITING; else -> stateOf(deriveJob) }
        steps += ProgressStep("derive", "汇总要点和待办", if (deriveState == StepState.DONE) 1 else 0, 1, deriveState)

        val jobs = listOfNotNull(importJob, deriveJob) + audio.flatMap { listOfNotNull(job("asr:${it.id}"), job("map:${it.id}:$rev")) }
        val running = jobs.filter { it.state == "RUNNING" }.maxByOrNull { it.updatedAt }
        val stuck = jobs.firstOrNull { it.state == "WAITING_CONFIG" || it.state == "WAITING_NETWORK" }
        val dead = jobs.firstOrNull { it.state == "FAILED" && it.attempts >= 5 }
        val waitingConsent = !ready && !consented && jobs.any { it.kind != "IMPORT" && it.state != "COMPLETE" }
        val sessionStuck = session.processingState in setOf("WAITING_CONFIG", "WAITING_NETWORK", "FAILED") && running == null &&
            jobs.none { it.state == "PENDING" || it.state == "WAITING_DEPENDENCY" || (it.state == "FAILED" && it.attempts < 5) }
        val reason = when {
            ready -> null
            waitingConsent -> ProcessingReason.AWAITING_CONSENT
            dead != null -> ProcessingReason.FAILED
            stuck != null -> ProcessingReason.code(stuck.error) ?: if (stuck.state == "WAITING_NETWORK") ProcessingReason.NETWORK else ProcessingReason.KEY_MISSING
            sessionStuck -> ProcessingReason.code(session.error) ?: if (session.processingState == "FAILED") ProcessingReason.FAILED else null
            else -> null
        }
        val detail = when {
            ready -> null
            reason == ProcessingReason.AWAITING_CONSENT -> "同意把原音发给转写服务后，会从这里继续"
            reason != null -> ProcessingReason.message((dead ?: stuck)?.error ?: session.error)
            else -> null
        }

        // weights: 解码 10 · 转写 60 · 整理 25 · 汇总 5; a running step counts half of the item it is working on
        fun part(step: ProgressStep): Float = if (step.state == StepState.DONE) 1f
            else ((step.done + if (step.state == StepState.RUNNING) 0.5f else 0f) / maxOf(step.total, 1)).coerceIn(0f, 1f)
        val weights = mapOf("import" to 10f, "asr" to 60f, "map" to 25f, "derive" to 5f)
        val fraction = if (ready) 1f else steps.sumOf { (weights.getValue(it.key) * part(it)).toDouble() }.toFloat() / steps.sumOf { weights.getValue(it.key).toDouble() }.toFloat()

        val runningSegment = segments.firstOrNull { it.transcribe == StepState.RUNNING }
        val headline = when {
            ready -> "整理完成"
            recording -> if (total == 0) "录音中 · 片段存好后开始转写" else "录音中 · 已转写 $transcribedCount/$total 段"
            reason == ProcessingReason.FAILED -> "处理失败"
            reason == ProcessingReason.AWAITING_CONSENT -> "等你确认数据外发"
            reason != null -> "已暂停 · ${reason.title}"
            importState != StepState.DONE -> if (importState == StepState.RUNNING) "正在解码导入的音频 · 已切出 $total 段" else "排队解码导入的音频"
            transcribeState != StepState.DONE -> when {
                segments.count { it.transcribe == StepState.RUNNING } > 1 -> "正在整场转写 · 共 $total 段"
                runningSegment != null -> "正在转写 · 第 ${runningSegment.index + 1}/$total 段"
                else -> "排队转写 · 已完成 $transcribedCount/$total 段"
            }
            organiseState != StepState.DONE -> segments.firstOrNull { it.organise == StepState.RUNNING }
                ?.let { "正在分段整理 · 第 ${it.index + 1}/$total 段" } ?: "排队整理 · 已完成 $organisedCount/$total 段"
            else -> if (deriveState == StepState.RUNNING) "正在汇总要点和待办" else "排队汇总要点和待办"
        }
        return ProcessingProgress(steps, segments, fraction, headline, detail, running?.updatedAt, reason, recording, ready)
    }
}
