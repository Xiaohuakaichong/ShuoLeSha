package com.example.shuolesa

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.navigation.AppNavigation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class NavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun threeTabsSettingsAndRecallNavigateWithoutMicrophonePermission() {
        val context:Context=ApplicationProvider.getApplicationContext()
        val app=AppContainer.from(context)
        runBlocking {withTimeout(10000) {app.awaitReady()}
            // v4.1: skip the consent gate (covered by its own tests)
            app.preferences.setPrivacyConsent(true);app.preferences.setOnboardingDone(true)}
        compose.setContent {ShuoLeSaTheme {AppNavigation(app)}}
        compose.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        compose.onNodeWithText("把说过的话留在这里").assertIsDisplayed()
        // v5.0: the dock shows a label only on the selected tab, so tabs are found by their accessibility description
        compose.onNodeWithContentDescription("今日").performClick()
        compose.onNodeWithText("当天复盘").assertIsDisplayed()
        compose.onNodeWithContentDescription("待办").performClick()
        compose.onNodeWithText("暂无候选").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithText("跟随系统").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("记忆").performClick()
        compose.onNodeWithText("搜索原话，或问问记忆").performClick()
        compose.onNodeWithText("从原话里找").assertIsDisplayed()
    }
}
