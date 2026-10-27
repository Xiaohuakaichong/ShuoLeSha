package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.prefs.ProviderPreset
import com.example.shuolesa.ui.ServiceConfigFlow
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
 * v4.3: a separate 整理 service is stored, and the data-outbound consent covers both addresses.
 * Keys stay blank: Robolectric has no AndroidKeyStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ServiceSplitPrefsTest {
    private lateinit var app: AppContainer
    private val asr=ProviderPreset.ALIYUN.defaultBaseUrl
    private val llm=ProviderPreset.DEEPSEEK.defaultBaseUrl
    private fun same()=AiSettings(ProviderPreset.ALIYUN.id,asr,"","qwen-audio-3.1-asr-flash","qwen3.8-flash","")
    private fun split()=same().copy(llmModel="deepseek-flash",llmProvider=ProviderPreset.DEEPSEEK.id,llmBaseUrl=llm)
    @Before fun setUp()=runBlocking {
        val context: Context=ApplicationProvider.getApplicationContext()
        app=AppContainer.from(context);withTimeout(10000) { app.awaitReady() }
        app.preferences.setPrivacyConsent(true)
        app.preferences.saveAiSettings(same())
        app.preferences.setOutboundConsent(asr)
    }

    @Test fun separateLlmServiceRoundTrips()=runBlocking {
        assertFalse(app.preferences.aiSettings().split)
        app.preferences.saveAiSettings(split())
        val saved=app.preferences.aiSettings()
        assertTrue(saved.split)
        assertEquals(ProviderPreset.DEEPSEEK.id,saved.llmProvider);assertEquals(llm,saved.llmBaseUrl)
        assertEquals("deepseek-flash",saved.llmModel);assertEquals(asr,saved.baseUrl)
        app.preferences.saveAiSettings(saved.copy(llmProvider="",llmBaseUrl="",llmModel="qwen3.8-flash"))
        assertFalse("back on the 转写 service",app.preferences.aiSettings().split)
    }
    @Test fun addingLlmServiceNeedsConsentForIt()=runBlocking {
        assertTrue(app.preferences.isOutboundConsented())
        val flow=ServiceConfigFlow(app.preferences) { }
        val pending=flow.requestSave(split(),false,saveAndConfirm=false)
        assertNotNull("a new 整理 address must be confirmed",pending)
        flow.confirm(pending!!)
        assertTrue(app.preferences.isOutboundConsented())
        assertEquals(AppPreferences.normalizeTarget(split().outboundTarget),app.preferences.outboundConsentBase.first())
        // the saved settings move the 整理 service elsewhere without asking -> no longer consented
        app.preferences.saveAiSettings(split().copy(llmBaseUrl="https://open.bigmodel.cn/api/paas/v4",llmProvider=ProviderPreset.ZHIPU.id))
        assertFalse(app.preferences.isOutboundConsented())
    }
    @Test fun movingBackToOneServiceNarrowsConsentWithoutAsking()=runBlocking {
        app.preferences.saveAiSettings(split());app.preferences.setOutboundConsent(split().outboundTarget)
        val flow=ServiceConfigFlow(app.preferences) { }
        assertNull("only an already-confirmed address remains",flow.requestSave(same(),false,saveAndConfirm=false))
        assertTrue(app.preferences.isOutboundConsented())
        assertEquals(AppPreferences.normalizeBase(asr),app.preferences.outboundConsentBase.first())
    }
}
