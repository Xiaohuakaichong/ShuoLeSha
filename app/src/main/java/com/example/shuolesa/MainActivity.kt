package com.example.shuolesa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.service.ShortcutDecision
import com.example.shuolesa.service.ShortcutTokens
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.ui.navigation.AppNavigation
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity() {
    private val microphone=registerForActivityResult(ActivityResultContracts.RequestPermission()) {if(it) startRecording()}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        val app=AppContainer.from(this)
        if(savedInstanceState==null) consumeAutoRecord(intent)
        setContent {
            val mode by app.preferences.themeMode.collectAsStateWithLifecycle("system")
            val systemDark=isSystemInDarkTheme();val dark=if(mode=="system") systemDark else mode=="dark"
            SideEffect {WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}}
            ShuoLeSaTheme(dark) {
                val initialization by app.initialization.collectAsStateWithLifecycle()
                when(val state=initialization) {
                    Initialization.Ready->AppNavigation(app)
                    Initialization.Loading->Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().safeDrawingPadding(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {Ceer(CeerPose.THINKING,Modifier.size(72.dp));Spacer(Modifier.height(16.dp));Text("正在打开记忆库",style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(16.dp));CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=3.dp)} }
                    is Initialization.Failed->Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {EmptyState("记忆库未能打开",state.message,CeerPose.ERROR)}
                }
            }
        }
    }
    override fun onStart() { super.onStart();AppContainer.from(this).onForeground() }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);consumeAutoRecord(intent)}
    /**
     * v4.1-a.3 (qa a.2 P1): MainActivity is exported, so it never trusts plain extras. The old
     * `auto_start_recording` / `consent_from_shortcut` extras are ignored. Only an intent carrying a one-time
     * in-process token from ShortcutTrampolineActivity is acted on, and even then MainActivity itself never
     * starts recording without the user (it only shows consent, asks for the mic, or shows "已开始录音").
     */
    private fun consumeAutoRecord(intent:Intent?) {
        intent ?: return
        val app=AppContainer.from(this)
        // v4.1: launcher shortcut "今天" -> screen ④. Navigation only, never records.
        if(intent.action==ACTION_OPEN_TODAY) { app.requestedTab.value=1;intent.action=Intent.ACTION_MAIN }
        if(intent.action==com.example.shuolesa.service.EventNotifications.ACTION_OPEN_TASKS) { app.requestedTab.value=2;intent.action=Intent.ACTION_MAIN }
        // v5.0: home screen widgets. Navigation only, like the launcher shortcut: they never record from here.
        if(intent.action==ACTION_OPEN_SEARCH) { app.requestedSearch.value=true;intent.action=Intent.ACTION_MAIN }
        val requested=intent.getStringExtra(EXTRA_INTERNAL_REQUEST)?.let { r -> ShortcutDecision.MainIntent.entries.firstOrNull { it.name==r } }
        val token=intent.getStringExtra(EXTRA_INTERNAL_TOKEN)
        listOf(EXTRA_INTERNAL_REQUEST,EXTRA_INTERNAL_TOKEN,"auto_start_recording","consent_from_shortcut").forEach(intent::removeExtra)
        when(ShortcutDecision.onMainIntent(requested,ShortcutTokens.shared.consume(token))) {
            ShortcutDecision.MainIntent.NONE -> Unit
            ShortcutDecision.MainIntent.SHOW_CONSENT -> lifecycleScope.launch {
                // re-check: consent may have been given meanwhile
                if(!app.preferences.privacyConsented.first()) app.shortcutConsent.value=true
            }
            ShortcutDecision.MainIntent.ASK_MICROPHONE -> lifecycleScope.launch { if(app.preferences.privacyConsented.first()) startOrAsk() }
            ShortcutDecision.MainIntent.SHOW_STARTED -> app.recordingStartedNotice.value=true
        }
    }
    override fun onResume() { super.onResume();visible=true }
    override fun onPause() { visible=false;super.onPause() }
    companion object {
        const val ACTION_OPEN_TODAY="com.example.shuolesa.action.OPEN_TODAY"
        const val ACTION_OPEN_SEARCH="com.example.shuolesa.action.OPEN_SEARCH"
        private const val EXTRA_INTERNAL_REQUEST="com.example.shuolesa.internal.REQUEST"
        private const val EXTRA_INTERNAL_TOKEN="com.example.shuolesa.internal.TOKEN"
        /** v4.1-a.3: true while MainActivity is resumed (app in the foreground). Main thread only. */
        @Volatile var visible=false
            private set
        /** Explicit in-app intent; only valid with a token from [ShortcutTokens.shared]. */
        fun internalIntent(context:android.content.Context,request:ShortcutDecision.MainIntent,token:String)=
            Intent(context,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_INTERNAL_REQUEST,request.name).putExtra(EXTRA_INTERNAL_TOKEN,token)
    }
    private fun startOrAsk() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startRecording()
        else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun startRecording() {if(!AudioCaptureService.isRunning) ContextCompat.startForegroundService(this,Intent(this,AudioCaptureService::class.java).setAction(AudioCaptureService.ACTION_START))}
}
