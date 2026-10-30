package com.example.shuolesa.network

import com.example.shuolesa.AppContainer
import com.example.shuolesa.audio.AudioRecorder
import com.example.shuolesa.audio.WavWriter
import com.example.shuolesa.data.prefs.AiSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/*
 * v5.2 实时字幕: text appears while the microphone is still recording. It is a preview only — the 原话 kept in the record
 * still comes from the per-片段 transcription after each 片段 is sealed, which is more accurate and knows speakers.
 *  - 百炼: DashScope realtime recognition over a WebSocket, words show up as they are spoken.
 *  - other services: a short WAV every few seconds through the same transcription call; silent stretches are skipped.
 * Both send audio, so both need the data-outbound consent (same gate as every other request) and can be switched off.
 */

enum class CaptionMode { OFF, CONNECTING, REALTIME, CHUNKED, UNAVAILABLE }

data class LiveCaption(val lines: List<String> = emptyList(), val partial: String = "", val mode: CaptionMode = CaptionMode.OFF,
                       val note: String? = null) {
    /** A finished sentence moves into [lines]; an unfinished one replaces [partial]. */
    fun withText(text: String, final: Boolean): LiveCaption {
        val t = text.trim()
        return if (final) copy(lines = if (t.isEmpty()) lines else (lines + t).takeLast(MAX_LINES), partial = "") else copy(partial = t)
    }
    val latest: String get() = partial.ifBlank { lines.lastOrNull().orEmpty() }
    val showing: Boolean get() = lines.isNotEmpty() || partial.isNotBlank() || mode == CaptionMode.CONNECTING || mode == CaptionMode.REALTIME || mode == CaptionMode.CHUNKED
    companion object { const val MAX_LINES = 200 }
}

object CaptionAudio {
    /** RMS below ~ -44 dBFS: nothing worth sending. */
    fun isSilent(pcm: ShortArray, count: Int = pcm.size): Boolean {
        if (count <= 0) return true
        var sum = 0.0
        for (i in 0 until count) { val s = pcm[i].toDouble(); sum += s * s }
        return sqrt(sum / count) / 32768.0 < 0.006
    }
    /** 16-bit little-endian, as DashScope expects for format=pcm. */
    fun bytes(pcm: ShortArray, count: Int): ByteArray {
        val out = ByteArray(count * 2)
        for (i in 0 until count) { val s = pcm[i].toInt(); out[2 * i] = (s and 0xff).toByte(); out[2 * i + 1] = (s shr 8).toByte() }
        return out
    }
}

/** DashScope realtime recognition (paraformer-realtime-v2, 16 kHz mono PCM). */
internal class DashScopeRealtime(private val client: AiClient, private val config: AiSettings) {
    sealed interface Event {
        data object Started : Event
        data class Text(val text: String, val final: Boolean) : Event
        data object Finished : Event
        data class Failed(val code: String, val message: String) : Event
        data object Other : Event
    }

    companion object {
        const val MODEL = "paraformer-realtime-v2"
        fun supports(config: AiSettings) = DashScopeAsr.isDashScope(config)
        fun url(config: AiSettings): String {
            val host = config.baseUrl.toHttpUrlOrNull()?.host.orEmpty()
            return if (host.startsWith("dashscope-intl")) "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference/"
            else "wss://dashscope.aliyuncs.com/api-ws/v1/inference/"
        }
        fun runTask(taskId: String): String = JSONObject()
            .put("header", JSONObject().put("action", "run-task").put("task_id", taskId).put("streaming", "duplex"))
            .put("payload", JSONObject().put("task_group", "audio").put("task", "asr").put("function", "recognition").put("model", MODEL)
                .put("parameters", JSONObject().put("format", "pcm").put("sample_rate", AudioRecorder.SAMPLE_RATE))
                .put("input", JSONObject())).toString()
        fun finishTask(taskId: String): String = JSONObject()
            .put("header", JSONObject().put("action", "finish-task").put("task_id", taskId).put("streaming", "duplex"))
            .put("payload", JSONObject().put("input", JSONObject())).toString()
        fun parse(message: String): Event {
            val obj = runCatching { JSONObject(message) }.getOrNull() ?: return Event.Other
            val header = obj.optJSONObject("header") ?: return Event.Other
            return when (header.optString("event")) {
                "task-started" -> Event.Started
                "result-generated" -> {
                    val sentence = obj.optJSONObject("payload")?.optJSONObject("output")?.optJSONObject("sentence") ?: return Event.Other
                    if (sentence.optBoolean("heartbeat", false)) Event.Other
                    else Event.Text(sentence.optString("text"), sentence.optBoolean("sentence_end", false))
                }
                "task-finished" -> Event.Finished
                "task-failed" -> Event.Failed(header.optString("error_code"), header.optString("error_message"))
                else -> Event.Other
            }
        }
    }

    /** Streams [audio] until it is closed; [onStarted] once the service accepted the task. */
    suspend fun run(audio: ReceiveChannel<ShortArray>, onStarted: () -> Unit, emit: (String, Boolean) -> Unit) {
        val taskId = UUID.randomUUID().toString().replace("-", "")
        val started = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        fun fail(e: Exception) { started.completeExceptionally(e); done.completeExceptionally(e) }
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(runTask(taskId)) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val e = parse(text)) {
                    Event.Started -> started.complete(Unit)
                    is Event.Text -> emit(e.text, e.final)
                    Event.Finished -> done.complete(Unit)
                    is Event.Failed -> fail(AiException(-1, "实时识别失败：${e.message.ifBlank { e.code }}"))
                    Event.Other -> Unit
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: 0
                fail(AiException(code, if (code == 401 || code == 403) "实时识别的密钥无效" else "实时识别连接失败"))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                started.completeExceptionally(AiException(0, "实时识别已断开")); done.complete(Unit)
            }
        }
        val socket = client.openWebSocket(Request.Builder().url(url(config)).header("Authorization", "Bearer ${config.apiKey}").build(), listener)
        try {
            withTimeout(10_000) { started.await() }
            onStarted()
            // ~100 ms per message, as the service recommends
            val batch = ShortArray(AudioRecorder.SAMPLE_RATE / 10); var filled = 0
            for (frame in audio) {
                if (done.isCompleted) { done.await(); break }
                var offset = 0
                while (offset < frame.size) {
                    val n = minOf(batch.size - filled, frame.size - offset)
                    System.arraycopy(frame, offset, batch, filled, n); filled += n; offset += n
                    if (filled == batch.size) {
                        if (!socket.send(CaptionAudio.bytes(batch, filled).toByteString())) throw AiException(0, "实时识别连接中断")
                        filled = 0
                    }
                }
            }
            if (filled > 0) socket.send(CaptionAudio.bytes(batch, filled).toByteString())
            socket.send(finishTask(taskId))
            withTimeoutOrNull(8_000) { done.await() }
        } finally { socket.close(1000, null) }
    }
}

/** Any other service: transcribe a short window at a time with the normal transcription call. */
internal class WindowedCaptions(private val client: AiClient, private val config: AiSettings, private val dir: File,
                                private val windowMs: Int = 6_000) {
    suspend fun run(audio: ReceiveChannel<ShortArray>, onIssue: (String) -> Unit, emit: (String, Boolean) -> Unit) = coroutineScope {
        val size = AudioRecorder.SAMPLE_RATE * windowMs / 1000
        val windows = Channel<ShortArray>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        launch {
            val buffer = ShortArray(size); var filled = 0
            try {
                for (frame in audio) {
                    var offset = 0
                    while (offset < frame.size) {
                        val n = minOf(size - filled, frame.size - offset)
                        System.arraycopy(frame, offset, buffer, filled, n); filled += n; offset += n
                        if (filled == size) { windows.send(buffer.copyOf()); filled = 0 }
                    }
                }
                if (filled > AudioRecorder.SAMPLE_RATE) windows.send(buffer.copyOf(filled)) // a last second or more
            } finally { windows.close() }
        }
        for (window in windows) {
            if (CaptionAudio.isSilent(window)) continue
            emit("…", false)
            val file = File(dir, "caption-${UUID.randomUUID()}.wav")
            try {
                val writer = WavWriter(file)
                try { writer.write(window, window.size) } finally { writer.finish() }
                emit(client.transcribe(config, file), true)
            } catch (e: CancellationException) { throw e }
            catch (e: AiException) {
                emit("", true)
                // a bad key or missing consent won't fix itself during this recording
                if (e.reason == ProcessingReason.KEY_INVALID || e.reason == ProcessingReason.AWAITING_CONSENT || e.reason == ProcessingReason.CLEARTEXT_BLOCKED) throw e
                onIssue("实时字幕跳过了一段：${e.message?.take(30)}")
            } finally { file.delete() }
        }
    }
}

/**
 * One per recording, owned by AudioCaptureService: [begin] when the microphone starts or resumes, [feed] every frame,
 * [end] on pause / stop. The screen and the notification read [state].
 */
class LiveCaptioner(private val app: AppContainer, private val scope: CoroutineScope) {
    companion object {
        private val mutable = MutableStateFlow(LiveCaption())
        val state = mutable.asStateFlow()
    }
    @Volatile private var channel: Channel<ShortArray>? = null
    private var job: Job? = null

    fun reset() { mutable.value = LiveCaption() }

    fun begin() {
        job?.cancel()
        // ~18 s of 60 ms frames; when the service falls behind the oldest audio is dropped from the preview
        val audio = Channel<ShortArray>(capacity = 300, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        channel = audio
        job = scope.launch {
            val prefs = app.preferences
            val blocked = when {
                !prefs.liveCaptions.first() -> CaptionMode.OFF to null
                !prefs.isOutboundConsented() -> CaptionMode.UNAVAILABLE to "确认数据外发后，录音时会显示实时字幕"
                else -> null
            }
            val config = prefs.aiSettings()
            val off = blocked ?: if (!config.configured) CaptionMode.UNAVAILABLE to "设置好转写服务后，录音时会显示实时字幕" else null
            if (off != null) { channel = null; audio.close(); mutable.update { it.copy(mode = off.first, note = off.second, partial = "") }; return@launch }
            val emit: (String, Boolean) -> Unit = { text, final -> mutable.update { it.withText(text, final) } }
            if (DashScopeRealtime.supports(config)) {
                mutable.update { it.copy(mode = CaptionMode.CONNECTING, note = null) }
                try {
                    DashScopeRealtime(app.ai, config).run(audio, { mutable.update { it.copy(mode = CaptionMode.REALTIME) } }, emit)
                    return@launch
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.update { it.copy(note = "实时识别没有连上，改为每隔几秒更新", partial = "") } }
            }
            mutable.update { it.copy(mode = CaptionMode.CHUNKED) }
            try { WindowedCaptions(app.ai, config, app.context.cacheDir).run(audio, { note -> mutable.update { it.copy(note = note) } }, emit) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(mode = CaptionMode.UNAVAILABLE, partial = "", note = "实时字幕已停止：${e.message?.take(40) ?: "请稍后再试"}") } }
        }
    }

    /** Called from the audio consumer for every frame; never blocks. */
    fun feed(pcm: ShortArray, count: Int) { channel?.trySend(pcm.copyOf(count)) }

    /** Paused or stopped: the engine finishes what it already has (at most a few seconds), then stops. */
    fun end() {
        channel?.close(); channel = null
        val running = job ?: return
        scope.launch { if (withTimeoutOrNull(10_000) { running.join() } == null) running.cancel(); mutable.update { it.copy(partial = "") } }
    }
}
