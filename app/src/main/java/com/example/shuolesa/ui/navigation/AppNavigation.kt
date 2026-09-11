package com.example.shuolesa.ui.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.shuolesa.ui.components.RecordingModeSelectSheet
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.DarkSurface
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.ui.screens.ActiveRecordingScreen
import com.example.shuolesa.ui.screens.AudioPlayerScreen
import com.example.shuolesa.ui.screens.LifeLogScreen
import com.example.shuolesa.ui.screens.NodeSettingsScreen
import com.example.shuolesa.ui.screens.TasksScreen
import com.example.shuolesa.ui.screens.TimelineScreen
import com.example.shuolesa.util.PermissionHelper
import kotlinx.coroutines.delay

/**
 * 主导航：底部 Tab + 录音/播放覆盖层。
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val repository = remember { AudioRepository(db.audioRecordDao()) }
    val permissionHelper = remember { PermissionHelper(context) }

    val launchStrategy by prefs.launchStrategy.collectAsState(initial = "default")
    var selectedTab by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(AudioCaptureService.isRunning) }
    var selectedRecord by remember { mutableStateOf<AudioRecordEntity?>(null) }
    var showModeSelectSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isRecording = AudioCaptureService.isRunning
            delay(500)
        }
    }

    val triggerStartRecord: (String?) -> Unit = { modeOverride ->
        if (permissionHelper.hasAllPermissions()) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                if (modeOverride != null) {
                    putExtra(AudioCaptureService.EXTRA_RECORDING_MODE, modeOverride)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        } else {
            Toast.makeText(context, "请先在设置中授予录音与麦克风权限", Toast.LENGTH_SHORT).show()
        }
    }

    val onStartRecordClick: () -> Unit = {
        if (launchStrategy == "prompt") {
            showModeSelectSheet = true
        } else {
            triggerStartRecord(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BgDark,
            floatingActionButton = {
                if (!isRecording && selectedTab in 0..1) {
                    FloatingActionButton(
                        onClick = onStartRecordClick,
                        containerColor = MintCyan,
                        contentColor = BgDark,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "快捷启动录音",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            },
            bottomBar = {
                if (!isRecording) {
                    BottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> TimelineScreen(
                        repository = repository,
                        prefs = prefs,
                        onRecordClick = { record -> selectedRecord = record },
                        onStartRecord = onStartRecordClick,
                        onNavigateToLifeLog = { selectedTab = 1 },
                    )
                    1 -> LifeLogScreen(
                        repository = repository,
                        prefs = prefs,
                    )
                    2 -> TasksScreen(
                        repository = repository,
                        onRecordClick = { record -> selectedRecord = record },
                    )
                    3 -> NodeSettingsScreen(
                        prefs = prefs,
                        permissionHelper = permissionHelper,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ActiveRecordingScreen()
        }

        AnimatedVisibility(
            visible = selectedRecord != null && !isRecording,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            selectedRecord?.let { record ->
                AudioPlayerScreen(
                    record = record,
                    repository = repository,
                    prefs = prefs,
                    onBack = { selectedRecord = null },
                )
            }
        }

        if (showModeSelectSheet) {
            RecordingModeSelectSheet(
                onDismiss = { showModeSelectSheet = false },
                onSelectMode = { mode ->
                    showModeSelectSheet = false
                    triggerStartRecord(mode)
                },
            )
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
        NavItem("记忆流", Icons.Default.Timeline),
        NavItem("生活手记", Icons.Default.AutoAwesome),
        NavItem("待办", Icons.Default.CheckCircleOutline),
        NavItem("设置", Icons.Default.Settings),
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
                    indicatorColor = NeonGreen.copy(alpha = 0.15f),
                ),
            )
        }
    }
}
