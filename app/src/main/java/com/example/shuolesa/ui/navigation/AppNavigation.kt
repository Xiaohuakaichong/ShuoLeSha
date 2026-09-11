package com.example.shuolesa.ui.navigation

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.AccentOn
import com.example.shuolesa.theme.AppColor
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.InkAtmosphere
import com.example.shuolesa.theme.ModeCasual
import com.example.shuolesa.ui.components.pressScale
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.ui.components.RecordingModeSelectSheet
import com.example.shuolesa.ui.screens.ActiveRecordingScreen
import com.example.shuolesa.ui.screens.AudioPlayerScreen
import com.example.shuolesa.ui.screens.LifeLogScreen
import com.example.shuolesa.ui.screens.NodeSettingsScreen
import com.example.shuolesa.ui.screens.TasksScreen
import com.example.shuolesa.ui.screens.TimelineScreen
import com.example.shuolesa.util.PermissionHelper
import kotlinx.coroutines.delay

/**
 * 主导航：记录 / 今日 | 录音 | 待办 / 设置。录音键几何居中。
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val repository = remember { AudioRepository(db.audioRecordDao()) }
    val permissionHelper = remember { PermissionHelper(context) }

    val launchStrategy by prefs.launchStrategy.collectAsState(initial = "default")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
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
        if (permissionHelper.hasMicPermission()) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                if (modeOverride != null) {
                    putExtra(AudioCaptureService.EXTRA_RECORDING_MODE, modeOverride)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        } else {
            Toast.makeText(context, "请先授予麦克风权限", Toast.LENGTH_SHORT).show()
            selectedTab = 3
        }
    }

    val onStartRecordClick: () -> Unit = {
        if (launchStrategy == "prompt") {
            showModeSelectSheet = true
        } else {
            triggerStartRecord(null)
        }
    }

    val onOpenSettings = { selectedTab = 3 }
    val mainChromeVisible = !isRecording && selectedRecord == null

    BackHandler(enabled = isRecording) {
        (context as? Activity)?.moveTaskToBack(true)
    }

    var lastBackExitAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = mainChromeVisible) {
        val now = System.currentTimeMillis()
        if (now - lastBackExitAt < 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackExitAt = now
            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) showModeSelectSheet = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        InkAtmosphere()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (mainChromeVisible) {
                    MainBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onRecordClick = onStartRecordClick,
                    )
                }
            },
        ) { paddingValues ->
            var visitedTabs by remember { mutableStateOf(setOf(selectedTab)) }
            LaunchedEffect(selectedTab) {
                visitedTabs = visitedTabs + selectedTab
            }
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                KeepAliveTab(selected = selectedTab == 0, visited = 0 in visitedTabs) {
                    TimelineScreen(
                        repository = repository,
                        prefs = prefs,
                        onRecordClick = { record -> selectedRecord = record },
                        onOpenSettings = onOpenSettings,
                    )
                }
                KeepAliveTab(selected = selectedTab == 1, visited = 1 in visitedTabs) {
                    LifeLogScreen(
                        repository = repository,
                        prefs = prefs,
                        onOpenSettings = onOpenSettings,
                        onRecordClick = { record -> selectedRecord = record },
                    )
                }
                KeepAliveTab(selected = selectedTab == 2, visited = 2 in visitedTabs) {
                    TasksScreen(
                        repository = repository,
                        onRecordClick = { record -> selectedRecord = record },
                        onOpenSettings = onOpenSettings,
                    )
                }
                KeepAliveTab(selected = selectedTab == 3, visited = 3 in visitedTabs) {
                    NodeSettingsScreen(
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
private fun KeepAliveTab(
    selected: Boolean,
    visited: Boolean,
    content: @Composable () -> Unit,
) {
    if (!visited) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer { alpha = if (selected) 1f else 0f }
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
            ),
    ) {
        content()
    }
}

private data class BottomTab(val label: String, val icon: ImageVector)

@Composable
private fun MainBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onRecordClick: () -> Unit,
) {
    val items = listOf(
        BottomTab("记录", Icons.Outlined.GraphicEq),
        BottomTab("今日", Icons.Default.Today),
        BottomTab("待办", Icons.Default.CheckCircleOutline),
        BottomTab("设置", Icons.Outlined.Settings),
    )

    val pillShape = RoundedCornerShape(34.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Dimens.gapMd, end = Dimens.gapMd, bottom = Dimens.gapSm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.bottomBarHeight)
                .shadow(
                    elevation = 18.dp,
                    shape = pillShape,
                    ambientColor = AppColor.accentShadow,
                    spotColor = AppColor.shadowTint,
                )
                .clip(pillShape)
                .background(AppColor.surfaceGlass)
                .border(Dimens.borderThin, AppColor.outline, pillShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabItem(
                tab = items[0],
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f),
            )
            BottomTabItem(
                tab = items[1],
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(Dimens.recordButton))
            BottomTabItem(
                tab = items[2],
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                modifier = Modifier.weight(1f),
            )
            BottomTabItem(
                tab = items[3],
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                modifier = Modifier.weight(1f),
            )
        }

        val recordInteraction = remember { MutableInteractionSource() }
        val recordPressed by recordInteraction.collectIsPressedAsState()
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .pressScale(recordPressed, 0.94f)
                .size(Dimens.recordButton)
                .shadow(
                    elevation = Dimens.fabElevation,
                    shape = CircleShape,
                    ambientColor = AppColor.accentShadow,
                    spotColor = AppColor.accentShadow,
                )
                .clip(CircleShape)
                .background(Accent)
                .clickable(
                    interactionSource = recordInteraction,
                    indication = null,
                    onClick = onRecordClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "开始录音",
                tint = AccentOn,
                modifier = Modifier.size(Dimens.fabIconSize),
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .pressScale(pressed, 0.96f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) Accent.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (selected) Accent else TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) TextPrimary else TextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
