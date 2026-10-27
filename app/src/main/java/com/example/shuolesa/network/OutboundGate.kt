package com.example.shuolesa.network

/**
 * v4.1-a.1 (qa-v41a P1-2): single source of truth for "nothing leaves the device before data-outbound consent".
 * Used by ProcessingCoordinator (enqueue / eligibility / runOne), AskMemory, SettingsViewModel.test (测试连接),
 * and enforced once more inside AiClient.send() so no caller can bypass it.
 */
object OutboundGate {
    const val BLOCKED_MESSAGE = "同意数据外发后才能联网处理"
    /** IMPORT only decodes a local file; every other job kind talks to the service. */
    fun allows(jobKind: String, outboundConsented: Boolean): Boolean = jobKind == "IMPORT" || outboundConsented
    fun blocked(): AiException = AiException(AiException.CONSENT, BLOCKED_MESSAGE, ProcessingReason.AWAITING_CONSENT)
    /** Throws [AiException] with [ProcessingReason.AWAITING_CONSENT] when not consented. */
    fun require(outboundConsented: Boolean) { if (!outboundConsented) throw blocked() }
}
