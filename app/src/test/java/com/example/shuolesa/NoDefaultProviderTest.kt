package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.prefs.ProviderPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v5.0: no service is preselected. StepFun stays available, as one choice among the others. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class NoDefaultProviderTest {
    /** A store of its own, so earlier tests that saved a provider cannot leak in. */
    private fun prefs(): AppPreferences { val context: Context=ApplicationProvider.getApplicationContext()
        val file=java.io.File.createTempFile("prefs",".preferences_pb").also { it.delete() }
        return AppPreferences(context,PreferenceDataStoreFactory.create { file }) }

    @Test fun freshInstallHasNoServiceSelected() = runBlocking {
        val prefs=prefs();prefs.initialize()
        val c=prefs.aiSettings()
        assertEquals("",c.provider);assertEquals("",c.baseUrl);assertEquals("",c.asrModel);assertEquals("",c.llmModel)
        assertFalse("nothing to send data to",c.configured)
        assertEquals("",prefs.providerMode.first());assertEquals("",prefs.baseUrl.first())
        assertFalse(prefs.isOutboundConsented())
    }
    @Test fun savingWithoutAServiceAsksForOne() {
        val e=try { prefs().validate(com.example.shuolesa.data.prefs.AiSettings("","","","","","")); null } catch(e: IllegalArgumentException) { e }
        assertEquals("请先选择转写服务商",e?.message)
    }
    @Test fun stepFunIsAnOrdinaryPreset() = runBlocking {
        assertNull(ProviderPreset.fromId(""));assertNull(ProviderPreset.fromId("no-such-provider"))
        assertEquals("所选服务",ProviderPreset.nameOf(""))
        assertNotEquals("not the first choice",ProviderPreset.STEPFUN,ProviderPreset.asrPresets.first())
        assertSame(ProviderPreset.STEPFUN,ProviderPreset.fromId("stepfun"))
        val prefs=prefs();prefs.initialize()
        prefs.applyPreset(ProviderPreset.STEPFUN)
        val c=prefs.aiSettings()
        assertEquals("stepfun",c.provider);assertEquals(ProviderPreset.STEPFUN.defaultBaseUrl,c.baseUrl)
    }
}
