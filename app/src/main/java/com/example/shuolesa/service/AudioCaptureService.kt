package com.example.shuolesa.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.shuolesa.audio.AudioRecorder
import com.example.shuolesa.audio.ChunkManager
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.UploadWorker
import com.example.shuolesa.util.HapticFeedback
import com.example.shuolesa.util.PhoneStateMonitor
import com.example.shuolesa.util.StorageChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that manages the recording pipeline:
 * AudioRecorder → OpusEncoder → ChunkManager → File
 */
class AudioCaptureService : Service() {

    companion object {
        private const val TAG = "AudioCaptureService"
        const val ACTION_START = "com.example.shuolesa.ACTION_START_RECORDING"
        const val ACTION_STOP = "com.example.shuolesa.ACTION_STOP_RECORDING"
        const val EXTRA_RECORDING_MODE = "com.example.shuolesa.EXTRA_RECORDING_MODE"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var elapsedMs = 0L
            private set

        @Volatile
        var currentRecordingMode: String = "lifelog"
            private set

        @Volatile
        var currentRecordingModeTitle: String = "🌿 LifeLog 随身省流记"
            private set

        @Volatile
        var currentRecordingFormatDesc: String = "AAC 硬件压缩 · 24kbps (~10MB/h)"
            private set

        // Callbacks for UI updates
        var onStateChanged: ((Boolean) -> Unit)? = null
        var onElapsedChanged: ((Long) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioRecorder: AudioRecorder? = null
    private var chunkManager: ChunkManager? = null
    private var phoneStateMonitor: PhoneStateMonitor? = null
    private var hapticFeedback: HapticFeedback? = null
    private lateinit var notificationHelper: NotificationHelper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        hapticFeedback = HapticFeedback(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_RECORDING_MODE)
                startRecording(mode)
            }
            ACTION_STOP, NotificationHelper.ACTION_FORCE_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording(overrideMode: String? = null) {
        if (isRunning) return

        // Check storage
        val storageChecker = StorageChecker(this)
        if (!storageChecker.hasEnoughStorage()) {
            Log.e(TAG, "Insufficient storage")
            serviceScope.launch {
                val prefs = AppPreferences(this@AudioCaptureService)
                if (prefs.isHapticEnabledSync()) {
                    hapticFeedback?.alarmPulse()
                }
            }
            stopSelf()
            return
        }

        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            stopSelf()
            return
        }

        // Start foreground
        val notification = notificationHelper.buildRecordingNotification()
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        // Acquire wake lock
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shuolesa:recording").apply {
            acquire()
        }

        // Initialize audio pipeline
        val recorder = AudioRecorder()

        if (!recorder.init()) {
            Log.e(TAG, "Failed to init AudioRecorder")
            stopSelf()
            return
        }

        val db = AppDatabase.getInstance(this)
        val repository = AudioRepository(db.audioRecordDao())
        val prefs = AppPreferences(this)
        val mode = overrideMode ?: kotlinx.coroutines.runBlocking { prefs.getRecordingModeSync() }
        val bitrate = kotlinx.coroutines.runBlocking { prefs.getLifelogBitrateKbpsSync() }
        val meetingFmt = kotlinx.coroutines.runBlocking { prefs.getMeetingFormatSync() }

        currentRecordingMode = mode
        currentRecordingModeTitle = if (mode == "meeting") "💼 高保真会议录音" else "🌿 LifeLog 随身省流记"
        currentRecordingFormatDesc = if (mode == "meeting") {
            if (meetingFmt == "wav") "无损 WAV · 16kHz 原始采样" else "高清 AAC · 64kbps"
        } else {
            val mbPerHour = when (bitrate) { 16 -> 7; 24 -> 10; 32 -> 14; else -> 10 }
            "AAC 硬件压缩 · ${bitrate}kbps (~${mbPerHour}MB/h)"
        }

        val chunkMgr = ChunkManager(
            context = this,
            repository = repository,
            scope = serviceScope,
            recordingMode = mode,
            lifelogBitrateKbps = bitrate,
            meetingFormat = meetingFmt,
        )

        audioRecorder = recorder
        chunkManager = chunkMgr

        // Start phone state monitor
        val monitor = PhoneStateMonitor(this)
        monitor.start(object : PhoneStateMonitor.PhoneStateCallback {
            override fun onCallStarted() {
                Log.d(TAG, "Call started, pausing recording")
                audioRecorder?.pause()
            }

            override fun onCallEnded() {
                Log.d(TAG, "Call ended")
                serviceScope.launch {
                    val prefs = AppPreferences(this@AudioCaptureService)
                    val autoResume = prefs.autoResumeAfterCall.first()
                    if (autoResume) {
                        Log.d(TAG, "Auto-resuming recording")
                        audioRecorder?.resume()
                    }
                }
            }
        })
        phoneStateMonitor = monitor

        isRunning = true
        onStateChanged?.invoke(true)

        // Start recording pipeline
        serviceScope.launch(Dispatchers.IO) {
            chunkMgr.startSession()

            recorder.startRecording(object : AudioRecorder.PcmCallback {
                override fun onPcmData(buffer: ShortArray, readCount: Int) {
                    chunkMgr.onPcmFrame(buffer, readCount)
                    elapsedMs = chunkMgr.elapsedMs
                    onElapsedChanged?.invoke(elapsedMs)
                }
            })
        }
    }

    private fun stopRecording() {
        if (!isRunning) return

        isRunning = false
        elapsedMs = 0
        onStateChanged?.invoke(false)

        // Stop recording pipeline
        audioRecorder?.stop()
        chunkManager?.finalizeSession()

        // Cleanup
        audioRecorder?.release()
        phoneStateMonitor?.stop()

        audioRecorder = null
        chunkManager = null
        phoneStateMonitor = null

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        // Schedule upload
        enqueueUpload()

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enqueueUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                UploadWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                uploadRequest,
            )
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }
}
