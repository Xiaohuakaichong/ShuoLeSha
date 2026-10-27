package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.network.*
import com.example.shuolesa.ui.ServiceConfigFlow
import com.example.shuolesa.ui.screens.TestButtonLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

/** Key left blank: Robolectric has no AndroidKeyStore (the draft check runs before any key use).
 *  Chief-of-staff spec: 「保存并确认」 -> confirm auto-tests once; cancel saves the address without consent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SaveAndConfirmFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var app: AppContainer
    private val consentedBase = "https://api.stepfun.com/step_plan/v1"
    @Before fun setUp() = runBlocking {
        server = MockWebServer(); server.start(InetAddress.getByName("127.0.0.1"), 0)
        val context: Context = ApplicationProvider.getApplicationContext()
        app = AppContainer.from(context); withTimeout(10000) { app.awaitReady() }
        app.preferences.setPrivacyConsent(true)
        app.preferences.saveAiSettings(AiSettings("custom", consentedBase, "", "asr", "llm", ""))
        app.preferences.setOutboundConsent(consentedBase)
    }
    @After fun tearDown() { server.shutdown() }
    private fun draft() = AiSettings("custom", server.url("/v1").toString().trimEnd('/'), "", "asr", "llm", "")

    @Test fun cancelSavesAddressKeepsConsentAndTestIsBlockedWithZeroRequests() = runBlocking {
        var tests = 0
        val flow = ServiceConfigFlow(app.preferences) { tests++ }
        val pending = flow.requestSave(draft(), false, saveAndConfirm = true)
        assertNotNull("new address needs re-confirm", pending)
        flow.cancel(pending!!)
        assertEquals("address saved", draft().baseUrl, app.preferences.aiSettings().baseUrl)
        assertEquals("consent unchanged", consentedBase, app.preferences.outboundConsentBase.first())
        assertFalse(app.preferences.isOutboundConsented())
        assertEquals("no auto test on cancel", 0, tests)
        assertFalse("测试连接 stays disabled", TestButtonLogic.enabled(false, consentedBase, draft().baseUrl))
        val e = try { ConnectionTester.run(app, app.preferences.aiSettings()); null } catch (x: AiException) { x }
        assertEquals(AiException.DRAFT_CHANGED, e!!.code)
        assertEquals(0, server.requestCount)
    }
    @Test fun confirmRecordsConsentAndRunsTestOnceAutomatically() = runBlocking {
        val tested = mutableListOf<AiSettings>()
        val flow = ServiceConfigFlow(app.preferences) { tested += it }
        val pending = flow.requestSave(draft(), false, saveAndConfirm = true)!!
        assertTrue(tested.isEmpty())
        flow.confirm(pending)
        assertEquals(1, tested.size)
        assertEquals(draft().baseUrl, tested.single().baseUrl)
        assertTrue(app.preferences.isOutboundConsented())
        assertTrue(TestButtonLogic.enabled(false, app.preferences.outboundConsentBase.first(), draft().baseUrl))
    }
    @Test fun plainSaveCancelStillSavesNothing() = runBlocking {
        val flow = ServiceConfigFlow(app.preferences) { fail("no test") }
        flow.cancel(flow.requestSave(draft(), false, saveAndConfirm = false)!!)
        assertEquals(consentedBase, app.preferences.aiSettings().baseUrl)
    }
}
