package com.example.shuolesa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.navigation.AppNavigation
import com.example.shuolesa.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled — UI will react to state changes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request runtime permissions
        val helper = PermissionHelper(this)
        if (!helper.hasAllPermissions()) {
            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
        }

        setContent {
            ShuoLeSaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PureBlack,
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
