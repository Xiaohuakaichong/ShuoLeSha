package com.example.shuolesa

import com.example.shuolesa.data.prefs.KeyNotice
import com.example.shuolesa.data.prefs.ServiceRegion
import com.example.shuolesa.data.prefs.ServiceRegion.*
import org.junit.Assert.*
import org.junit.Test

/** v4.1-a.1 (qa-v41a P1-1, PRD a.1): 境内 / 局域网 / 未知 / 境外. Only known-overseas is called overseas. */
class ServiceRegionTest {
    private fun assertRegion(expected: ServiceRegion, vararg bases: String) =
        bases.forEach { assertEquals(it, expected, ServiceRegion.of(it)) }

    @Test fun domestic() = assertRegion(DOMESTIC,
        "https://api.stepfun.com/step_plan/v1", "https://api.siliconflow.cn/v1", "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "https://ark.cn-beijing.volces.com/api/v3", "https://api.deepseek.com/v1", "https://my-gateway.example.cn/v1", "https://API.STEPFUN.COM/v1")

    @Test fun lanIncludesPrivateIpsAndLocalhost() = assertRegion(LAN,
        "http://localhost:8000/v1", "http://127.0.0.1:8000/v1", "http://10.0.2.2:8000/v1",
        "https://10.1.2.3/v1", "https://172.16.0.5/v1", "https://172.31.255.1/v1", "https://192.168.1.10:8443/v1",
        "https://169.254.10.1/v1", "https://100.100.1.2/v1", "https://nas.local/v1", "https://gateway.lan/v1",
        "https://[::1]:8000/v1", "https://[fd12:3456::1]/v1", "https://[fe80::1]/v1")

    @Test fun overseasOnlyWhenKnown() = assertRegion(OVERSEAS,
        "https://api.openai.com/v1", "https://api.anthropic.com/v1", "https://generativelanguage.googleapis.com/v1beta",
        "https://api.groq.com/openai/v1", "https://openrouter.ai/api/v1", "https://api.x.ai/v1")

    @Test fun everythingElseIsUnknown() = assertRegion(UNKNOWN,
        "https://my-gateway.example.com/v1", "https://shuolesha.mydomain.net/v1", "https://8.8.8.8/v1",
        "https://172.32.0.1/v1", "https://172.15.0.1/v1", "https://192.169.1.1/v1", "https://11.0.0.1/v1",
        "https://notopenai.com/v1", "not a url", "")

    @Test fun warningsAndTones() {
        assertEquals("你的录音数据将传输到境外处理", OVERSEAS.warning)
        assertEquals("无法判断服务所在地，如在境外将发生数据出境", UNKNOWN.warning)
        assertNull(LAN.warning); assertNull(DOMESTIC.warning)
        assertEquals("仅在本地网络内传输", LAN.note)
        assertEquals(Tone.OK, DOMESTIC.tone); assertEquals(Tone.RUN, LAN.tone)
        assertEquals(Tone.WARN, UNKNOWN.tone); assertEquals(Tone.WARN, OVERSEAS.tone)
        assertEquals(listOf("境内", "局域网", "未知", "境外"), ServiceRegion.entries.map { it.tag })
    }

    @Test fun privateAddressBoundaries() {
        assertTrue(ServiceRegion.isPrivateAddress("172.16.0.0")); assertTrue(ServiceRegion.isPrivateAddress("172.31.255.255"))
        assertFalse(ServiceRegion.isPrivateAddress("172.32.0.0")); assertFalse(ServiceRegion.isPrivateAddress("01.2.3.4"))
        assertFalse(ServiceRegion.isPrivateAddress("10.0.0")); assertFalse(ServiceRegion.isPrivateAddress("256.1.1.1"))
    }

    /** ⓪-3 (design a.1): same provider reuses its saved key; provider change needs a key. */
    @Test fun reconfirmKeyNotice() {
        assertEquals(KeyNotice.REUSE_SAVED, KeyNotice.of("stepfun", "stepfun", "", "sk-saved", false))
        assertEquals(KeyNotice.REUSE_SAVED, KeyNotice.of("stepfun", "stepfun", "sk-saved", "sk-saved", false))
        assertEquals(KeyNotice.USE_ENTERED, KeyNotice.of("stepfun", "stepfun", "sk-new", "sk-saved", false))
        assertEquals(KeyNotice.NEW_SERVICE_NEEDS_KEY, KeyNotice.of("stepfun", "siliconflow", "", "", false))
        assertTrue(KeyNotice.NEW_SERVICE_NEEDS_KEY.blocksConfirm)
        assertEquals("新服务需要填写密钥", KeyNotice.NEW_SERVICE_NEEDS_KEY.text)
        assertEquals(KeyNotice.NEW_SERVICE_KEY_ENTERED, KeyNotice.of("stepfun", "siliconflow", "sk-sf", "sk-sf", false))
        assertEquals(KeyNotice.LOCAL_NO_KEY, KeyNotice.of("stepfun", "custom", "", "", true))
        assertFalse(KeyNotice.LOCAL_NO_KEY.blocksConfirm)
    }
}
