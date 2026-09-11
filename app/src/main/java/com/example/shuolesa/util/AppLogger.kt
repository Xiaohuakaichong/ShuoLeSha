package com.example.shuolesa.util

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.work.WorkManager
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 集中式日志记录器与运行诊断系统：
 * 1. 维护内存环形日志队列（最近 500 条日志）与本地日志文件
 * 2. 自动收集设备系统、权限、网络连通性、数据库录音队列、WorkManager 与 Logcat 状态
 * 3. 支持一键复制诊断日志、导出 TXT 文件并调用系统分享
 */
object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_BUFFER_LINES = 500
    private val logQueue = ConcurrentLinkedDeque<String>()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    @Volatile
    private var isInitialized = false
    private var logFile: File? = null

    fun init(app: Application) {
        if (isInitialized) return
        isInitialized = true
        try {
            val logDir = File(app.filesDir, "logs").apply { mkdirs() }
            logFile = File(logDir, "shuolesa_app.log")
            i(TAG, "=== ShuoLeSha App Started (Version: 2.1.5, SDK: ${Build.VERSION.SDK_INT}) ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AppLogger log file", e)
        }
    }

    fun d(tag: String, msg: String) {
        log(Log.DEBUG, tag, msg, null)
    }

    fun i(tag: String, msg: String) {
        log(Log.INFO, tag, msg, null)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        log(Log.WARN, tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        log(Log.ERROR, tag, msg, tr)
    }

    private fun log(priority: Int, tag: String, msg: String, tr: Throwable?) {
        val levelStr = when (priority) {
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO "
            Log.WARN -> "WARN "
            Log.ERROR -> "ERROR"
            else -> "LOG  "
        }
        val timestamp = timeFormat.format(Date())
        val fullMsg = if (tr != null) {
            "$msg\n${Log.getStackTraceString(tr)}"
        } else msg

        val line = "[$timestamp] [$levelStr/$tag] $fullMsg"

        // Write to Android logcat
        when (priority) {
            Log.DEBUG -> Log.d(tag, fullMsg)
            Log.INFO -> Log.i(tag, fullMsg)
            Log.WARN -> Log.w(tag, fullMsg)
            Log.ERROR -> Log.e(tag, fullMsg)
            else -> Log.println(priority, tag, fullMsg)
        }

        // Buffer in memory
        logQueue.add(line)
        while (logQueue.size > MAX_BUFFER_LINES) {
            logQueue.poll()
        }

        // Append to file asynchronously or on background
        logFile?.let { file ->
            try {
                if (file.length() > 5 * 1024 * 1024L) { // Rotate if > 5MB
                    val backup = File(file.parentFile, "shuolesa_app.log.bak")
                    if (backup.exists()) backup.delete()
                    file.renameTo(backup)
                }
                FileWriter(file, true).use { writer ->
                    writer.appendLine(line)
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 生成完整的 Markdown / 文本格式诊断报告
     */
    suspend fun generateDiagnosticReport(context: Context): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        sb.appendLine("==================================================")
        sb.appendLine("        说了啥 (ShuoLeSha) 系统运行与诊断报告        ")
        sb.appendLine("==================================================")
        sb.appendLine("• 导出时间: $nowStr")
        sb.appendLine("• 应用版本: v3.0.2 (versionCode: 11)")
        sb.appendLine("• 包名: ${context.packageName}")
        sb.appendLine()

        // 1. 设备与硬件
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[1. 硬件与系统信息]")
        sb.appendLine("• 设备品牌: ${Build.BRAND}")
        sb.appendLine("• 制造厂商: ${Build.MANUFACTURER}")
        sb.appendLine("• 设备型号: ${Build.MODEL}")
        sb.appendLine("• 硬件代码: ${Build.HARDWARE}")
        sb.appendLine("• Android 系统: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("• 系统指纹: ${Build.FINGERPRINT}")
        sb.appendLine("• 支持 ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine()

        // 2. 权限与系统状态
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[2. 系统权限与保活优化]")
        val permissionHelper = PermissionHelper(context)
        val hasMic = permissionHelper.hasPermission(android.Manifest.permission.RECORD_AUDIO)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionHelper.hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        } else true
        val isA11y = permissionHelper.isAccessibilityServiceEnabled()
        val isBatteryIgnored = permissionHelper.isBatteryOptimizationIgnored()
        val isIgnoringPower = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (_: Exception) { false }

        sb.appendLine("• 麦克风录音权限: ${if (hasMic) "✅ 允许" else "❌ 未授权"}")
        sb.appendLine("• 通知推送权限: ${if (hasNotif) "✅ 允许" else "❌ 未授权"}")
        sb.appendLine("• 无障碍盲操监听服务: ${if (isA11y) "✅ 已开启" else "⚠️ 未开启"}")
        sb.appendLine("• 忽略电池优化(防系统杀后台): ${if (isBatteryIgnored || isIgnoringPower) "✅ 已加白" else "⚠️ 受系统电池优化限制"}")
        sb.appendLine()

        // 3. 网络连通性
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[3. 网络连通性与约束分析]")
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNet)

            if (caps == null) {
                sb.appendLine("• 网络状态: ❌ 无活跃网络连接 (Offline)")
            } else {
                val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                val netTypeStr = when {
                    isWifi -> "Wi-Fi"
                    isCellular -> "移动蜂窝网络 (Cellular)"
                    isVpn -> "VPN 代理网络"
                    else -> "其他网络"
                }
                sb.appendLine("• 接入类型: $netTypeStr")
                sb.appendLine("• 具备互联网能力 (INTERNET): $hasInternet")
                sb.appendLine("• 系统网络校验通过 (VALIDATED): $isValidated")

                if (hasInternet && !isValidated) {
                    sb.appendLine("  ⚠️ 警告：系统将当前网络标记为 [未经验证/受限]。在很多国产定制系统(MIUI/EMUI/ColorOS)上，因 gstatic.com 连通检测被墙可能导致此项为 false，从而使得 WorkManager 的 CONNECTED 约束无限期延迟触发！")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("• 网络检测异常: ${e.message}")
        }
        sb.appendLine()

        // 4. 配置与端点脱敏状态
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[4. AI 端点与音质配置]")
        try {
            val prefs = AppPreferences(context)
            val baseUrl = prefs.getBaseUrlSync()
            val apiKey = prefs.getApiKeySync()
            val asrModel = prefs.getAsrModelSync()
            val llmModel = prefs.getLlmModelSync()
            val keepLocal = prefs.isKeepLocalAudioSync()

            val keyMasked = when {
                apiKey.isBlank() -> "⚠️ 【未配置/空白】(会导致录音一直无法上传并卡在排队中！)"
                apiKey.length <= 8 -> "${apiKey.take(2)}****"
                else -> "${apiKey.take(4)}...${apiKey.takeLast(4)} (字符长度: ${apiKey.length})"
            }

            sb.appendLine("• 服务端点 Base URL: $baseUrl")
            sb.appendLine("• 语音识别 ASR 模型: $asrModel")
            sb.appendLine("• 提炼总结 LLM 模型: $llmModel")
            sb.appendLine("• API Key 凭证状态: $keyMasked")
            sb.appendLine("• 本地音频保留策略: ${if (keepLocal) "始终保留" else "提炼成功后删除"}")
        } catch (e: Exception) {
            sb.appendLine("• 读取配置异常: ${e.message}")
        }
        sb.appendLine()

        // 5. 存储空间与文件状态
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[5. 磁盘存储与录音文件状态]")
        try {
            val filesDir = context.filesDir
            val freeMb = filesDir.freeSpace / (1024 * 1024)
            val totalMb = filesDir.totalSpace / (1024 * 1024)
            val recordingsDir = File(filesDir, "recordings")
            val audioFiles = recordingsDir.listFiles() ?: emptyArray()

            sb.appendLine("• 内部存储空间: 可用 $freeMb MB / 总共 $totalMb MB")
            sb.appendLine("• 录音文件目录: ${recordingsDir.absolutePath} (存在: ${recordingsDir.exists()})")
            sb.appendLine("• 录音文件总数: ${audioFiles.size} 个")
            if (audioFiles.isNotEmpty()) {
                sb.appendLine("• 最近录音文件清单 (最近 10 个):")
                audioFiles.sortedByDescending { it.lastModified() }.take(10).forEach { file ->
                    val kb = file.length() / 1024
                    val mtime = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
                    sb.appendLine("   - ${file.name} ($kb KB, $mtime)")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("• 存储检查异常: ${e.message}")
        }
        sb.appendLine()

        // 6. 数据库录音队列核验
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[6. 数据库录音记录与队列排查]")
        try {
            val db = AppDatabase.getInstance(context)
            val dao = db.audioRecordDao()
            val allRecords = dao.getAll()

            val pending = allRecords.filter { it.status == AudioRecordEntity.STATUS_PENDING }
            val uploading = allRecords.filter { it.status == AudioRecordEntity.STATUS_UPLOADING }
            val failed = allRecords.filter { it.status == AudioRecordEntity.STATUS_FAILED }
            val uploaded = allRecords.filter { it.status == AudioRecordEntity.STATUS_UPLOADED }

            sb.appendLine("• 数据库录音总数: ${allRecords.size} 条")
            sb.appendLine("• ⏳ 排队中 (PENDING): ${pending.size} 条")
            sb.appendLine("• ⚡ 处理中 (UPLOADING): ${uploading.size} 条")
            sb.appendLine("• ❌ 上传/识别失败 (FAILED): ${failed.size} 条")
            sb.appendLine("• ✅ 已完成 (UPLOADED): ${uploaded.size} 条")
            sb.appendLine()

            // Detailed abnormal records
            val abnormalRecords = pending + uploading + failed
            if (abnormalRecords.isNotEmpty()) {
                sb.appendLine("• 异常/排队中录音逐条排查清单 (共 ${abnormalRecords.size} 条):")
                abnormalRecords.take(15).forEach { rec ->
                    val localFile = File(rec.filePath)
                    val fileExists = localFile.exists()
                    val actualLen = if (fileExists) localFile.length() else -1L
                    val dateStr = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(rec.createdAt))

                    sb.appendLine("   [#${rec.id}] 状态: ${rec.status} | 模式: ${rec.recordingMode ?: "默认"} | 格式: ${rec.audioFormat ?: "未知"}")
                    sb.appendLine("       创建时间: $dateStr | 时长: ${rec.durationMs / 1000}s | 重试次数: ${rec.retryCount}")
                    sb.appendLine("       录音路径: ${rec.filePath}")
                    sb.appendLine("       文件存在: $fileExists (磁盘大小: $actualLen bytes, 数据库记录: ${rec.fileSizeBytes} bytes)")
                    if (!fileExists || actualLen == 0L) {
                        sb.appendLine("       ⚠️ 严重警告：录音文件不存在或为 0 字节！")
                    }
                    if (!rec.title.isNullOrBlank()) {
                        sb.appendLine("       标题: ${rec.title}")
                    }
                    if (!rec.summary.isNullOrBlank()) {
                        sb.appendLine("       备注/错误信息: ${rec.summary}")
                    }
                }
            }
        } catch (e: Exception) {
            sb.appendLine("• 数据库核查异常: ${e.message}")
        }
        sb.appendLine()

        // 7. WorkManager 调度任务状态
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[7. WorkManager 后台任务状态]")
        try {
            val wm = WorkManager.getInstance(context)
            val workInfos = wm.getWorkInfosForUniqueWork("audio_upload_work").get()
            sb.appendLine("• 唯一任务名: audio_upload_work")
            sb.appendLine("• 任务条目数: ${workInfos.size}")
            workInfos.forEachIndexed { index, info ->
                sb.appendLine("   #$index: ID=${info.id}, 状态=${info.state}, 重试次数=${info.runAttemptCount}")
                sb.appendLine("       标签: ${info.tags.joinToString(", ")}")
            }
            if (workInfos.isEmpty()) {
                sb.appendLine("   (暂无活跃或已保存的 WorkManager 任务)")
            }
        } catch (e: Exception) {
            sb.appendLine("• WorkManager 检查异常: ${e.message}")
        }
        sb.appendLine()

        // 8. 内存实时操作日志
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[8. 应用内部操作流日志 (最近 ${logQueue.size} 条)]")
        val snapshotLogs = logQueue.toList().takeLast(100)
        if (snapshotLogs.isEmpty()) {
            sb.appendLine("(暂无内存日志)")
        } else {
            snapshotLogs.forEach { sb.appendLine(it) }
        }
        sb.appendLine()

        // 9. Logcat 截取
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("[9. 系统 Logcat (筛选 ShuoLeSha 相关标签)]")
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "120", "-s", "ShuoLeSha:V", "UploadWorker:V", "ApiService:V", "AudioCaptureService:V", "ChunkManager:V", "AndroidRuntime:E"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var logcatLineCount = 0
            var l = reader.readLine()
            while (l != null && logcatLineCount < 120) {
                sb.appendLine(l)
                logcatLineCount++
                l = reader.readLine()
            }
            reader.close()
            process.destroy()
        } catch (e: Exception) {
            sb.appendLine("• 获取 Logcat 失败: ${e.message}")
        }

        sb.appendLine("==================================================")
        sb.appendLine("                    [报告结束]                    ")
        sb.appendLine("==================================================")

        sb.toString()
    }

    /**
     * 导出诊断报告到缓存目录中的 txt 文件
     */
    suspend fun exportDiagnosticReportFile(context: Context): File = withContext(Dispatchers.IO) {
        val report = generateDiagnosticReport(context)
        val dateStr = fileDateFormat.format(Date())
        val cacheDir = File(context.cacheDir, "logs").apply { mkdirs() }
        val reportFile = File(cacheDir, "shuolesa_diagnostic_$dateStr.txt")
        reportFile.writeText(report, Charsets.UTF_8)
        d(TAG, "Diagnostic report exported to: ${reportFile.absolutePath}")
        reportFile
    }

    /**
     * 弹出系统分享面板，将诊断报告 txt 文件分享给微信、QQ、网盘、邮件等
     */
    suspend fun shareDiagnosticReport(context: Context) {
        try {
            val file = exportDiagnosticReportFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "说了啥运行诊断报告")
                putExtra(Intent.EXTRA_TEXT, "这是从“说了啥”导出的系统运行与排队核查诊断日志，请查阅附件。")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "分享或导出诊断日志").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e(TAG, "Failed to share diagnostic report", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 复制诊断报告内容到系统剪贴板
     */
    suspend fun copyDiagnosticReportToClipboard(context: Context): String {
        val report = generateDiagnosticReport(context)
        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("说了啥运行诊断日志", report)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "✅ 诊断报告已复制到剪贴板！可直接粘贴发送", Toast.LENGTH_LONG).show()
        }
        return report
    }
}
