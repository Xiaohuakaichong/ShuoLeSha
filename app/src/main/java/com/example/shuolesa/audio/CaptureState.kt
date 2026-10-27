package com.example.shuolesa.audio

enum class CapturePhase { IDLE, STARTING, RECORDING, PAUSED, STOPPING, ERROR }
data class CaptureState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val sessionId: String? = null,
    val mode: String = "lifelog",
    val durationMs: Long = 0,
    val savedChunks: Int = 0,
    val level: Float = 0f,
    val error: String? = null,
    /** v4.2: marks of this recording so far, as offsets from its start (录音页「标记刚才这句」). */
    val marks: List<Long> = emptyList(),
) {
    val active: Boolean get() = phase in listOf(CapturePhase.STARTING,CapturePhase.RECORDING,CapturePhase.PAUSED,CapturePhase.STOPPING)
    val modeLabel: String get() = if(mode=="meeting") "会议" else "随身"
}
