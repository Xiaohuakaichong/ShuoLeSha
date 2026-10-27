package com.example.shuolesa

import com.example.shuolesa.ui.screens.TestButtonLogic
import org.junit.Assert.*
import org.junit.Test

/** UI review P2-8: the real form's 测试连接 enabled/disabled rule (Settings and onboarding ①). */
class TestButtonLogicTest {
    private val consented = "https://api.stepfun.com/step_plan/v1"
    @Test fun enabledOnlyWhenIdleAndDraftIsConsented() {
        assertTrue(TestButtonLogic.enabled(false, consented, "https://API.stepfun.com:443/step_plan/v1/"))
        assertFalse("busy", TestButtonLogic.enabled(true, consented, consented))
        assertFalse("draft changed", TestButtonLogic.enabled(false, consented, "https://gw.example.com/v1"))
        assertFalse("no consent at all", TestButtonLogic.enabled(false, null, consented))
        assertFalse("blank draft", TestButtonLogic.enabled(false, consented, ""))
    }
    @Test fun yellowBannerOnlyForADifferentConsentedAddress() {
        assertTrue(TestButtonLogic.draftMismatch(consented, "https://gw.example.com/v1"))
        assertFalse("same after normalising", TestButtonLogic.draftMismatch(consented, "https://api.stepfun.com/step_plan/v1/"))
        assertFalse("no consent -> the 去确认 notice, not the banner", TestButtonLogic.draftMismatch(null, "https://gw.example.com/v1"))
    }
}
