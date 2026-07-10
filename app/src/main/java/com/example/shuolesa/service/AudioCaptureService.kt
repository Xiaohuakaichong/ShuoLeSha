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
import com.example.shuolesa.audio.OpusEncoder
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

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var elapsedMs = 0L
            private set

        // Callbacks for UI updates
        var onStateChanged: ((Boolean) -> Unit)? = null
        var onElapsedChanged: ((Long) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioRecorder: AudioRecorder? = null
    private var opusEncoder: OpusEncoder? = null
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
            ACTION_START -> startRecording()
            ACTION_STOP, NotificationHelper.ACTION_FORCE_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
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
        val encoder = OpusEncoder()

        if (!recorder.init()) {
            Log.e(TAG, "Failed to init AudioRecorder")
            stopSelf()
            return
        }

        if (!encoder.init()) {
            Log.e(TAG, "Failed to init OpusEncoder")
            recorder.release()
            stopSelf()
            return
        }

        val db = AppDatabase.getInstance(this)
        val repository = AudioRepository(db.audioRecordDao())
        val chunkMgr = ChunkManager(this, encoder, repository, serviceScope)

        audioRecorder = recorder
        opusEncoder = encoder
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
            val outputStream = chunkMgr.startSession()
            encoder.start(outputStream)

            recorder.startRecording(object : AudioRecorder.PcmCallback {
                override fun onPcmData(buffer: ShortArray, readCount: Int) {
                    encoder.encode(buffer, readCount)
                    chunkMgr.onFrameEncoded()
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
        opusEncoder?.release()
        phoneStateMonitor?.stop()

        audioRecorder = null
        opusEncoder = null
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
