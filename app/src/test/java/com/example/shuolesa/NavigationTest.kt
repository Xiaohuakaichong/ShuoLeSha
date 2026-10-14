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
        runBlocking {withTimeout(10000) {app.awaitReady()}}
        compose.setContent {ShuoLeSaTheme {AppNavigation(app)}}
        compose.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        compose.onNodeWithText("把说过的话留在这里").assertIsDisplayed()
        compose.onNodeWithText("今日").performClick()
        compose.onNodeWithText("原稿覆盖").assertIsDisplayed()
        compose.onNodeWithText("待办").performClick()
        compose.onNodeWithText("暂无候选事项").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithText("跟随系统").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("记忆").performClick()
        compose.onNodeWithText("找一句话，或问问记忆").performClick()
        compose.onNodeWithText("输入关键词或问题").assertIsDisplayed()
    }
}
