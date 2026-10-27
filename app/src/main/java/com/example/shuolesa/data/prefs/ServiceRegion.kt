package com.example.shuolesa.data.prefs

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * v4.1-a.1: where the configured service lives, for the data-outbound consent pages (PIPL art. 38/39).
 * Four categories (PRD a.1 / qa-v41a P1-1):
 *  - DOMESTIC: known mainland-China providers or a `.cn` host
 *  - LAN: localhost, loopback, emulator host, RFC1918 / link-local / ULA IPs, `.local` / `.lan` hosts
 *  - OVERSEAS: only hosts we positively know are outside mainland China
 *  - UNKNOWN: everything else (custom domains, public IPs). The user confirms; we never guess "overseas".
 */
enum class ServiceRegion(val label: String, val tag: String, val warning: String?, val note: String?) {
    DOMESTIC("境内服务", "境内", null, null),
    LAN("局域网 / 本机", "局域网", null, "仅在本地网络内传输"),
    UNKNOWN("未知地区", "未知", "无法判断服务所在地，如在境外将发生数据出境", null),
    OVERSEAS("境外服务", "境外", "你的录音数据将传输到境外处理", null);

    /** Tag colour per design a.1: 境内 ok 绿 / 局域网 run 蓝 / 未知、境外 warn 黄. */
    val tone: Tone get() = when(this) { DOMESTIC -> Tone.OK; LAN -> Tone.RUN; UNKNOWN, OVERSEAS -> Tone.WARN }
    enum class Tone { OK, RUN, WARN }

    companion object {
        private val CN_HOSTS = listOf(
            "stepfun.com", "siliconflow.cn", "aliyuncs.com", "volces.com", "volcengine.com", "baidubce.com",
            "moonshot.cn", "deepseek.com", "bigmodel.cn", "xfyun.cn", "tencentcloudapi.com", "minimaxi.com",
        )
        private val OVERSEAS_HOSTS = listOf(
            "openai.com", "anthropic.com", "googleapis.com", "groq.com", "x.ai", "mistral.ai", "cohere.com",
            "together.xyz", "fireworks.ai", "deepgram.com", "assemblyai.com", "openrouter.ai", "azure.com",
            "elevenlabs.io", "minimax.io", "siliconflow.com",
        )
        private val LAN_SUFFIXES = listOf(".local", ".lan", ".internal", ".home.arpa", ".localhost")
        /**
         * v4.2: regional endpoints of providers listed in [CN_HOSTS] that are outside mainland China. Checked before
         * [CN_HOSTS], otherwise e.g. 百炼 Singapore (dashscope-intl.aliyuncs.com) would be shown as 境内.
         */
        private val OVERSEAS_REGIONAL = listOf("dashscope-intl.aliyuncs.com", ".ap-southeast-1.maas.aliyuncs.com",
            ".us-east-1.maas.aliyuncs.com")

        fun hostOf(base: String): String = base.trim().toHttpUrlOrNull()?.host ?: base.trim()

        fun of(base: String): ServiceRegion {
            val host = hostOf(base).lowercase().trim('[', ']').trimEnd('.')
            if (host.isEmpty()) return UNKNOWN
            if (host in AiSettings.CLEARTEXT_HOSTS || isPrivateAddress(host) || LAN_SUFFIXES.any { host.endsWith(it) }) return LAN
            if (OVERSEAS_REGIONAL.any { if (it.startsWith(".")) host.endsWith(it) else host == it }) return OVERSEAS
            if (host.endsWith(".cn") || CN_HOSTS.any { host == it || host.endsWith(".$it") }) return DOMESTIC
            if (OVERSEAS_HOSTS.any { host == it || host.endsWith(".$it") }) return OVERSEAS
            return UNKNOWN
        }

        /** IPv4 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, 100.64/10 (CGNAT, e.g. Tailscale); IPv6 ::1, fc00::/7, fe80::/10. */
        fun isPrivateAddress(host: String): Boolean {
            val v4 = host.split('.')
            if (v4.size == 4 && v4.all { p -> p.toIntOrNull()?.let { it in 0..255 && p == it.toString() } == true }) {
                val (a, b) = v4.take(2).map { it.toInt() }
                return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                    (a == 169 && b == 254) || (a == 100 && b in 64..127)
            }
            if (':' in host) {
                val h = host.lowercase()
                return h == "::1" || h.startsWith("fc") || h.startsWith("fd") ||
                    h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb")
            }
            return false
        }
    }
}

/**
 * v4.1-a.1 ⓪-3: which key notice the re-confirm sheet shows (design a.1).
 * Same provider, only the address changed -> reuse the key saved for that provider.
 * Provider changed -> "新服务需要填写密钥" (confirm disabled while the key is still empty).
 */
enum class KeyNotice(val text: String, val blocksConfirm: Boolean) {
    REUSE_SAVED("会沿用该服务商已保存的密钥", false),
    USE_ENTERED("将使用你刚填写的密钥", false),
    NEW_SERVICE_NEEDS_KEY("新服务需要填写密钥", true),
    NEW_SERVICE_KEY_ENTERED("新服务需要填写密钥 · 将使用表单里填写的密钥", false),
    LOCAL_NO_KEY("本地服务可以不填密钥", false);

    companion object {
        fun of(savedProvider: String, newProvider: String, enteredKey: String, savedKeyForNewProvider: String, local: Boolean): KeyNotice {
            val providerChanged = savedProvider != newProvider
            return when {
                providerChanged && enteredKey.isBlank() && local -> LOCAL_NO_KEY
                providerChanged && enteredKey.isBlank() -> NEW_SERVICE_NEEDS_KEY
                providerChanged -> NEW_SERVICE_KEY_ENTERED
                enteredKey.isBlank() || enteredKey == savedKeyForNewProvider -> if (savedKeyForNewProvider.isBlank() && local) LOCAL_NO_KEY else REUSE_SAVED
                else -> USE_ENTERED
            }
        }
    }
}
