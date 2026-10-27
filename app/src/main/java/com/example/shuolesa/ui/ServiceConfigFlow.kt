package com.example.shuolesa.ui

import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.AppPreferences

/**
 * Chief-of-staff spec (v4.1-final): 「保存并确认」 on the 地址已修改 banner.
 *  - save the draft, then open the outbound re-confirm dialog;
 *  - confirm  -> consent recorded for the new address, then the connection test runs once automatically;
 *  - cancel   -> the new address IS saved but NOT consented (old consent kept), so 测试连接 stays blocked
 *                (ConnectionTester returns DRAFT_CHANGED, nothing sent).
 * Kept free of ViewModel/Android types so it can be tested with the real AppPreferences.
 * v4.3: the "address" is [AiSettings.outboundTarget] (转写, plus 整理 when it has its own service).
 */
class ServiceConfigFlow(private val prefs: AppPreferences, private val runTest: suspend (AiSettings) -> Unit) {
    data class Pending(val config: AiSettings, val clearKey: Boolean, val autoTest: Boolean, val saveOnCancel: Boolean)

    /** Returns a pending confirmation when the address needs re-consent, or null when saved directly. */
    suspend fun requestSave(config: AiSettings, clearKey: Boolean, saveAndConfirm: Boolean): Pending? {
        prefs.validate(config)
        if (!prefs.needsOutboundReconfirm(config.outboundTarget)) {
            prefs.saveAiSettings(config, clearKey)
            // v4.3: every address is already confirmed; record exactly what is used now (narrows a 转写 + 整理 consent).
            prefs.setOutboundConsent(config.outboundTarget)
            if (saveAndConfirm) runTest(prefs.aiSettings())
            return null
        }
        return Pending(config, clearKey, autoTest = saveAndConfirm, saveOnCancel = saveAndConfirm)
    }
    suspend fun confirm(p: Pending) {
        prefs.saveAiSettings(p.config, p.clearKey)
        prefs.setOutboundConsent(p.config.outboundTarget)
        if (p.autoTest) runTest(prefs.aiSettings())
    }
    /** 「保存配置」 cancel keeps the old behaviour (nothing saved); 「保存并确认」 cancel saves without consent. */
    suspend fun cancel(p: Pending) { if (p.saveOnCancel) prefs.saveAiSettings(p.config, p.clearKey) }
}
