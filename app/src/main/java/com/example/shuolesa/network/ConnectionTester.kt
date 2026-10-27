package com.example.shuolesa.network

import com.example.shuolesa.AppContainer
import com.example.shuolesa.audio.WavWriter
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Shared 测试连接 (Settings and onboarding ①). Sends a 1 s silent WAV + one chat call, so it is outbound data.
 *
 * qa v4.1-final P1: Settings tests the *unsaved draft*, while AiClient's gate only knows the *saved* address.
 * So before anything is sent, the draft address must equal the consented address under the same
 * normalisation as the consent check ([AppPreferences.normalizeBase]: scheme + host lowercased, default
 * port dropped, trailing slash removed). Otherwise it is refused locally with [AiException.DRAFT_CHANGED].
 * v4.3: the "address" is [AiSettings.outboundTarget] — 转写, plus 整理 when it has its own service.
 */
object ConnectionTester {
    const val DRAFT_CHANGED_MESSAGE = "地址已修改，需重新确认数据外发"
    const val DRAFT_CHANGED_BANNER = DRAFT_CHANGED_MESSAGE

    /** True when [draftBase] (an address or a 转写 + 整理 target) is what the user consented to. A null consent never matches. */
    fun matchesConsent(consentedBase: String?, draftBase: String): Boolean =
        consentedBase != null && AppPreferences.normalizeTarget(consentedBase) == AppPreferences.normalizeTarget(draftBase)

    /** Gate order: no consent at all -> CONSENT; consent for another address -> DRAFT_CHANGED. */
    fun requireDraftConsented(consented: Boolean, consentedBase: String?, draftBase: String) {
        OutboundGate.require(consented)
        if (!matchesConsent(consentedBase, draftBase))
            throw AiException(AiException.DRAFT_CHANGED, DRAFT_CHANGED_MESSAGE, ProcessingReason.AWAITING_CONSENT)
    }

    /** Pure core (no AppContainer) so it can be tested against a MockWebServer. */
    suspend fun run(ai: AiClient, config: AiSettings, consented: Boolean, consentedBase: String?, cacheDir: File) {
        requireDraftConsented(consented, consentedBase, config.outboundTarget)
        config.endpoints.forEach { checkCleartext(it) }
        config.validate()
        val file = File(cacheDir, "connection-test.wav")
        try {
            val writer = WavWriter(file)
            try { writer.write(ShortArray(16000), 16000) } finally { writer.finish() }
            // v4.3: with two services, say which one failed.
            role(config.split, "转写服务") { ai.transcribe(config, file) }
            role(config.split, "整理服务") { ai.chat(config, "请仅回答连接成功", "这是一次连接测试", false) }
        } finally { file.delete() }
    }

    private suspend fun role(label: Boolean, name: String, block: suspend () -> Unit) {
        try { block() } catch (e: AiException) {
            if (!label) throw e
            throw AiException(e.code, "$name：${e.message}", e.reason, e.retryAfterMs)
        }
    }

    /**
     * "consented" here = privacy accepted and *some* address confirmed; whether it is *this* address is the
     * draft check. (isOutboundConsented() compares the saved address, which after「保存并确认」→ cancel is the
     * new, unconfirmed one, so it would report CONSENT instead of the more precise DRAFT_CHANGED.)
     */
    suspend fun run(app: AppContainer, config: AiSettings) {
        val base = app.preferences.outboundConsentBase.first()
        run(app.ai, config, app.preferences.privacyConsented.first() && base != null, base, app.context.cacheDir)
    }
}
