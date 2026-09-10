package com.example.shuolesa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.navigation.AppNavigation
import com.example.shuolesa.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled — UI will react to state changes
        handleAutoRecordIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request runtime permissions
        val helper = PermissionHelper(this)
        if (!helper.hasAllPermissions()) {
            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
        } else {
            handleAutoRecordIntent(intent)
        }

        setContent {
            ShuoLeSaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark,
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
            if (helper.hasAllPermissions()) {
                val recordIntent = Intent(this, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_START
                }
                ContextCompat.startForegroundService(this, recordIntent)
            }
        }
    }
}

