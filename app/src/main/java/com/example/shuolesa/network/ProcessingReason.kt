package com.example.shuolesa.network

/**
 * v4.1-a P0-2: machine-readable reason for WAITING_CONFIG / WAITING_NETWORK / FAILED.
 * Stored without a schema change as a "[CODE] " prefix of the existing `error` column on
 * processing_jobs and recording_sessions, e.g. "[KEY_INVALID] 服务密钥无效，请重新填写".
 * UI must render [message] (prefix stripped) and branch on [code].
 */
enum class ProcessingReason(val title: String) {
    KEY_MISSING("还没填服务密钥"),
    KEY_INVALID("密钥失效"),
    ADDRESS_INVALID("地址不对"),
    /** v4.1-a.1: http address rejected by cleartext policy (network_security_config). A config problem, never "等网络". */
    CLEARTEXT_BLOCKED("服务地址被拦截"),
    AWAITING_CONSENT("等确认"),
    NETWORK("等网络"),
    RATE_LIMITED("服务繁忙"),
    FAILED("处理失败");

    fun encode(message: String) = "[$name] $message"

    companion object {
        private val PREFIX = Regex("^\\[([A-Z_]+)] ?")
        fun code(error: String?): ProcessingReason? {
            val match = PREFIX.find(error ?: return null) ?: return null
            return entries.firstOrNull { it.name == match.groupValues[1] }
        }
        fun message(error: String?): String? = error?.replace(PREFIX, "")
        /** Config problems the user fixes on the service page. */
        fun needsServiceFix(reason: ProcessingReason?) = reason == KEY_MISSING || reason == KEY_INVALID || reason == ADDRESS_INVALID || reason == CLEARTEXT_BLOCKED
    }
}
