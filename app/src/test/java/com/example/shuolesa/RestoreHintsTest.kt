package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.ui.logic.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 设置 › 外观「恢复所有提示」clears every「以后不提示」 flag and nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class RestoreHintsTest {
    @Test fun everyDismissibleNoticeHasAKeyAndBannersHaveNone() {
        NoticeKind.entries.forEach { assertEquals(it.name, it.dismissible, it.prefKey != null) }
        assertEquals(NoticeKind.entries.count { it.dismissible }, NoticePolicy.dismissalKeys.toSet().size)
    }
    @Test fun pureClearRemovesOnlyDismissalKeys() {
        val before = mapOf<String, Any?>("blind_fallback_hint_off" to true, "home_blind_card_off" to true, "theme_mode" to "dark")
        assertEquals(mapOf<String, Any?>("theme_mode" to "dark"), NoticePolicy.cleared(before))
    }
    @Test fun clearAllHintDismissalsResetsPersistedFlags() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val app = AppContainer.from(context)
        withTimeout(10000) { app.awaitReady() }
        val p = app.preferences
        p.setBlindHintOff(true); p.setHomeBlindCardOff(true); p.setThemeMode("dark")
        assertTrue(p.blindHintOff.first()); assertTrue(p.homeBlindCardOff.first())
        p.clearAllHintDismissals()
        assertFalse(p.blindHintOff.first()); assertFalse(p.homeBlindCardOff.first())
        assertEquals("dark", p.themeMode.first())
    }
}
