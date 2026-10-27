package com.example.shuolesa

import com.example.shuolesa.ui.logic.*
import org.junit.Assert.*
import org.junit.Test

/** v4.1 decisions: 「以后不提示」 only for informational cards; 测试连接 never blocks onboarding. */
class NoticePolicyTest {
    @Test fun statusBannersCanNeverBeDismissed() {
        BannerKind.entries.forEach { k ->
            val n = NoticeKind.of(k)
            assertFalse(NoticePolicy.canDismissForever(n))
            assertTrue("$k ignores a dismissed flag while active", NoticePolicy.visible(n, active = true, dismissedForever = true))
            assertFalse("$k disappears once resolved", NoticePolicy.visible(n, active = false, dismissedForever = false))
        }
    }
    @Test fun informationalCardsRespectPersistedDismissal() {
        listOf(NoticeKind.BLIND_SYSTEM_LIMIT, NoticeKind.HOME_BLIND_FALLBACK).forEach { n ->
            assertTrue(NoticePolicy.canDismissForever(n))
            assertTrue(NoticePolicy.visible(n, true, false))
            assertFalse(NoticePolicy.visible(n, true, true))
            assertFalse(NoticePolicy.visible(n, false, false))
        }
    }
    @Test fun bannerPickStillShowsFailedEvenIfUserWantedSilence() {
        // there is no dismiss input to BannerLogic at all: failed stays until retried/resolved
        val b = BannerLogic.pick(listOf(BannerInput("s", "FAILED", null, "x")), false)
        assertNotNull(b); assertEquals(BannerKind.FAILED, b!!.kind)
        assertNull(BannerLogic.pick(listOf(BannerInput("s", "COMPLETE", null, null)), false))
    }
    @Test fun connectionTestFailureIsReadableAndNonBlocking() {
        assertEquals("同意数据外发后才能联网处理", ConnectionTestLogic.failed("[AWAITING_CONSENT] 同意数据外发后才能联网处理").error)
        assertEquals(ConnectionTestLogic.FALLBACK_ERROR, ConnectionTestLogic.failed(null).error)
        assertEquals(ConnectionTestLogic.FALLBACK_ERROR, ConnectionTestLogic.failed("  [HTTP_401]  ").error)
        assertEquals("密钥无效", ConnectionTestLogic.failed("密钥无效").error)
        assertEquals(ConnTestPhase.FAILED, ConnectionTestLogic.failed("x").phase)
        assertTrue(ConnectionTestLogic.failed("x").canTest) // can retry
    }
    @Test fun connectionTestStartRules() {
        assertFalse(ConnectionTestLogic.canStart("", ConnTestUi()))
        assertFalse(ConnectionTestLogic.canStart("k", ConnectionTestLogic.testing()))
        assertTrue(ConnectionTestLogic.canStart("k", ConnectionTestLogic.ok()))
    }
}
