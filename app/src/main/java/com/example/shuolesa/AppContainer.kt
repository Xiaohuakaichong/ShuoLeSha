package com.example.shuolesa

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.MemoryRepository
import com.example.shuolesa.network.AiClient
import com.example.shuolesa.network.ProcessingCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

sealed interface Initialization {
    data object Loading: Initialization
    data object Ready: Initialization
    data class Failed(val message: String): Initialization
}
class AppContainer private constructor(val context: Context) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val preferences=AppPreferences(context)
    val database by lazy { AppDatabase.getInstance(context) }
    val repository by lazy { MemoryRepository(database,context,scope) }
    val ai=AiClient(outboundAllowed={ preferences.isOutboundConsented() })
    /** v4.1-a.1: opened from the tile / blind trigger / launcher shortcut before privacy consent. */
    val shortcutConsent=MutableStateFlow(false)
    /** v4.1-a.1: one-shot message for the home snackbar (e.g. "已同意，点录音即可开始"). */
    val homeMessage=MutableStateFlow<String?>(null)
    /** v4.1-a.3: tile started recording while the app was in the foreground -> Snackbar "已开始录音 · 查看". */
    val recordingStartedNotice=MutableStateFlow(false)
    /** v4.1: home tab requested from outside (launcher shortcut 今天 = 1, notification 待办 = 2). */
    val requestedTab=MutableStateFlow<Int?>(null)
    /** v5.0: 快捷条 widget → open search. */
    val requestedSearch=MutableStateFlow(false)
    val processing by lazy { ProcessingCoordinator(context,repository,preferences,ai,scope) }
    private val _initialization=MutableStateFlow<Initialization>(Initialization.Loading)
    val initialization=_initialization.asStateFlow()
    private val ready=CompletableDeferred<Unit>()
    fun initialize() {
        scope.launch {
            try {
                preferences.initialize()
                database.openHelper.writableDatabase
                processing.recover()
                _initialization.value=Initialization.Ready;ready.complete(Unit)
                processing.requestProcessing()
                watchWidgets()
                val connectivity=context.getSystemService(ConnectivityManager::class.java)
                runCatching {connectivity.registerDefaultNetworkCallback(object: ConnectivityManager.NetworkCallback() {
                    // v4.1.1: onCapabilitiesChanged also fires for every signal-strength / bandwidth update. Waking
                    // the queue (DB writes + a full drain) on each of those drained battery on mobile data, so the
                    // queue is woken only when the default network gains internet access or becomes validated.
                    @Volatile private var current: Network?=null
                    @Volatile private var internet=false
                    @Volatile private var validated=false
                    // A new default network: its capabilities callback follows and decides whether to wake.
                    override fun onAvailable(network: Network) { current=network;internet=false;validated=false }
                    override fun onLost(network: Network) { if(network==current) { current=null;internet=false;validated=false } }
                    override fun onCapabilitiesChanged(network: Network,capabilities: NetworkCapabilities) {
                        val hasInternet=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        val isValidated=hasInternet && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        val gained=(hasInternet && !internet) || (isValidated && !validated)
                        internet=hasInternet;validated=isValidated
                        if(gained) wakeNetwork()
                    }
                    private fun wakeNetwork() { scope.launch { repository.dao.wakeWaiting(false);processing.requestProcessing();processing.wakeConfigWaiting() } } // v4.1-a: also WAITING_CONFIG, rate limited
                })}
            } catch(e: Exception) {
                _initialization.value=Initialization.Failed("数据未被清空。无法打开记忆库：${e.message?.take(160)}")
                ready.completeExceptionally(e)
            }
        }
    }
    suspend fun awaitReady() { ready.await() }
    /** v5.0: 待办清单 / 今日回顾 widgets follow the database; collectLatest + delay = a short debounce, and nothing runs without widgets. */
    private fun watchWidgets() {
        scope.launch { repository.dao.observeTasks().collectLatest { delay(600);if(com.example.shuolesa.widget.Widgets.hasAny(context)) com.example.shuolesa.widget.Widgets.refreshData(context) } }
        scope.launch { repository.dao.observeSessions().collectLatest { delay(1_000);if(com.example.shuolesa.widget.Widgets.hasAny(context)) com.example.shuolesa.widget.TodayWidgetProvider.refresh(context) } }
        scope.launch { repository.dao.observeDigests().collectLatest { delay(1_000);if(com.example.shuolesa.widget.Widgets.hasAny(context)) com.example.shuolesa.widget.TodayWidgetProvider.refresh(context) } }
    }
    /** v4.1-a: called when the app comes to the foreground; retries WAITING_CONFIG at most every 5 min. */
    fun onForeground() { if(_initialization.value==Initialization.Ready) scope.launch { processing.wakeConfigWaiting() } }
    companion object {
        // The factory always passes applicationContext; never retain an Activity.
        @Suppress("StaticFieldLeak") @Volatile private var instance: AppContainer?=null
        fun from(context: Context): AppContainer = instance ?: synchronized(this) {
            instance ?: AppContainer(context.applicationContext).also { instance=it;it.initialize() }
        }
    }
}
