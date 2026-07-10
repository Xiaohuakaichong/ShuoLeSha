package com.example.shuolesa.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.DarkSurface
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.ui.screens.ActiveRecordingScreen
import com.example.shuolesa.ui.screens.AudioPlayerScreen
import com.example.shuolesa.ui.screens.NodeSettingsScreen
import com.example.shuolesa.ui.screens.TimelineScreen
import com.example.shuolesa.util.PermissionHelper
import kotlinx.coroutines.delay

/**
 * Main app navigation with bottom tabs and recording overlay.
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val repository = remember { AudioRepository(db.audioRecordDao()) }
    val permissionHelper = remember { PermissionHelper(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(AudioCaptureService.isRunning) }
    var selectedRecord by remember { mutableStateOf<AudioRecordEntity?>(null) }

    // Poll recording state
    LaunchedEffect(Unit) {
        while (true) {
            isRecording = AudioCaptureService.isRunning
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content with bottom nav
        Scaffold(
            containerColor = PureBlack,
            bottomBar = {
                if (!isRecording) {
                    BottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> NodeSettingsScreen(
                        prefs = prefs,
                        permissionHelper = permissionHelper,
                    )
                    1 -> TimelineScreen(
                        repository = repository,
                        onRecordClick = { record -> selectedRecord = record },
                    )
                }
            }
        }

        // Recording overlay (full screen on top)
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ActiveRecordingScreen()
        }

        // Audio player overlay
        AnimatedVisibility(
            visible = selectedRecord != null && !isRecording,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            selectedRecord?.let { record ->
                AudioPlayerScreen(
                    record = record,
                    onBack = { selectedRecord = null },
                )
            }
        }
    }
}

@Composable
private fun BottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    data class NavItem(val label: String, val icon: ImageVector)

    val items = listOf(
        NavItem("配置", Icons.Default.Settings),
        NavItem("时间线", Icons.Default.Timeline),
    )

    NavigationBar(
        containerColor = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonGreen,
                    selectedTextColor = NeonGreen,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = NeonGreen.copy(alpha = 0.1f),
                ),
            )
        }
    }
}
