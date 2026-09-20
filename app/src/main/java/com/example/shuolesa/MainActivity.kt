package com.example.shuolesa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.AppColor
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.navigation.AppNavigation
import com.example.shuolesa.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        handleAutoRecordIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val helper = PermissionHelper(this)
        if (!helper.hasAllPermissions()) {
            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
        } else {
            handleAutoRecordIntent(intent)
        }

        setContent {
            val prefs = remember { AppPreferences(this) }
            val themeMode by prefs.themeMode.collectAsState(initial = "light")
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            ShuoLeSaTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppColor.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoRecordIntent(intent)
    }

    private fun handleAutoRecordIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("auto_start_recording", false) == true) {
            val helper = PermissionHelper(this)
            if (!helper.hasAllPermissions()) return
            // API Key 卫生：无 Key 时不静默开录（AudioCaptureService 也会二次拦截）
            kotlinx.coroutines.runBlocking {
                val prefs = AppPreferences(this@MainActivity)
                if (prefs.requiresApiKeySync()) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "请先在设置中配置 API Key",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    return@runBlocking
                }
                val recordIntent = Intent(this@MainActivity, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_START
                }
                ContextCompat.startForegroundService(this@MainActivity, recordIntent)
            }
        }
    }
}
