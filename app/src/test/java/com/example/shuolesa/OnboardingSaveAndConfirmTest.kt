package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.prefs.ProviderPreset
import com.example.shuolesa.network.*
import com.example.shuolesa.ui.ServiceConfigFlow
import com.example.shuolesa.ui.screens.TestButtonLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI review P1: onboarding ①「保存并确认」 uses the same ServiceConfigFlow as Settings, wired exactly as in
 * AppNavigation (applyPreset(STEPFUN) -> requestSave(aiSettings(), saveAndConfirm=true) -> confirm/cancel).
 * Outbound confirm comes first; only confirm auto-tests (once); cancel stays blocked and sends nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class OnboardingSaveAndConfirmTest {
    private lateinit var app: AppContainer
    private val otherConsented = "https://gw.example.com/v1"
    private val stepfun = ProviderPreset.STEPFUN.defaultBaseUrl
    @Before fun setUp() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        app = AppContainer.from(context); withTimeout(10000) { app.awaitReady() }
        app.preferences.setPrivacyConsent(true)
        app.preferences.setOutboundConsent(otherConsented) // consent recorded for another address earlier
        app.preferences.applyPreset(ProviderPreset.STEPFUN)
    }
    @Test fun mismatchDisablesTestAndShowsBanner() = runBlocking {
        val consented = app.preferences.outboundConsentBase.first()
        assertTrue(TestButtonLogic.draftMismatch(consented, stepfun))
        assertFalse(TestButtonLogic.enabled(false, consented, stepfun))
    }
    @Test fun confirmFirstThenAutoTestOnce() = runBlocking {
        var tests = 0
        val flow = ServiceConfigFlow(app.preferences) { tests++ }
        val pending = flow.requestSave(app.preferences.aiSettings(), false, saveAndConfirm = true)
        assertNotNull("outbound confirm must come first", pending)
        assertEquals("no test before confirm", 0, tests)
        flow.confirm(pending!!)
        assertEquals(1, tests)
        assertTrue(app.preferences.isOutboundConsented())
        assertEquals(AppPreferencesNormal(stepfun), app.preferences.outboundConsentBase.first())
    }
    @Test fun cancelIsBlockedAndSendsNothing() = runBlocking {
        var tests = 0
        val flow = ServiceConfigFlow(app.preferences) { tests++ }
        flow.cancel(flow.requestSave(app.preferences.aiSettings(), false, saveAndConfirm = true)!!)
        assertEquals(0, tests)
        assertEquals(otherConsented, app.preferences.outboundConsentBase.first())
        // AiClient that counts any attempt to send: must stay 0
        var sent = 0
        val ai = AiClient(outboundAllowed = { sent++; true })
        val base = app.preferences.outboundConsentBase.first()
        val e = try { ConnectionTester.run(ai, app.preferences.aiSettings(), true, base, app.context.cacheDir); null } catch (x: AiException) { x }
        assertEquals(AiException.DRAFT_CHANGED, e!!.code)
        assertEquals(0, sent)
    }
    private fun AppPreferencesNormal(s: String) = com.example.shuolesa.data.prefs.AppPreferences.normalizeBase(s)
}
