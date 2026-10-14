package com.example.shuolesa

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.MemoryCard
import com.example.shuolesa.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class UiInteractionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun topicCardOpensAndShowsCandidateCount() {
        var opened=false
        compose.setContent {ShuoLeSaTheme {MemoryCardView(MemoryCard("t","s","t","周五交付","先完成原型再确认交期",1,"lifelog",60000,"READY",2,false)) {opened=true}}}
        compose.onNodeWithText("2 项候选待办").assertIsDisplayed()
        compose.onNodeWithText("周五交付").performClick()
        compose.runOnIdle {assertTrue(opened)}
    }
    @Test fun sourceCardCarriesExactAnchor() {
        val ref=EvidenceRef(9,10,17,"周五交付原稿。")
        var opened:EvidenceRef?=null
        compose.setContent {ShuoLeSaTheme(true) {SourceCard(ref,{opened=it})}}
        compose.onNodeWithText("“周五交付原稿。”").performClick()
        compose.runOnIdle {assertEquals(ref,opened)}
    }
    @Test fun ceerStatesHaveAccessibleLabelsInBothThemes() {
        compose.setContent {ShuoLeSaTheme(true) {Column {CeerPose.entries.forEach {Ceer(it,Modifier.size(40.dp))}}}}
        CeerPose.entries.forEach {compose.onNodeWithContentDescription(it.label).assertExists()}
    }
}
