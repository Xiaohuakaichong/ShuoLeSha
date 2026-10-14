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
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.ui.navigation.AppNavigation

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
                    Initialization.Loading->Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {Ceer(CeerPose.THINKING);Spacer(Modifier.height(16.dp));Text("正在打开记忆库");Spacer(Modifier.height(16.dp));CircularProgressIndicator()} }
                    is Initialization.Failed->Surface(Modifier.fillMaxSize()) {EmptyState("记忆库未能打开",state.message,CeerPose.ERROR)}
                }
            }
        }
    }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);consumeAutoRecord(intent)}
    private fun consumeAutoRecord(intent:Intent?) {
        if(intent?.getBooleanExtra("auto_start_recording",false)!=true) return
        intent.removeExtra("auto_start_recording")
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startRecording()
        else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun startRecording() {if(!AudioCaptureService.isRunning) ContextCompat.startForegroundService(this,Intent(this,AudioCaptureService::class.java).setAction(AudioCaptureService.ACTION_START))}
}
