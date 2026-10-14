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

sealed interface Initialization {
    data object Loading: Initialization
    data object Ready: Initialization
    data class Failed(val message: String): Initialization
}
class AppContainer private constructor(val context: Context) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val preferences=AppPreferences(context)
    val database by lazy { AppDatabase.getInstance(context) }
    val repository by lazy { MemoryRepository(database,context) }
    val ai=AiClient()
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
                val connectivity=context.getSystemService(ConnectivityManager::class.java)
                runCatching {connectivity.registerDefaultNetworkCallback(object: ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { wakeNetwork() }
                    override fun onCapabilitiesChanged(network: Network,capabilities: NetworkCapabilities) { wakeNetwork() }
                    private fun wakeNetwork() { scope.launch { repository.dao.wakeWaiting(false);processing.requestProcessing() } }
                })}
            } catch(e: Exception) {
                _initialization.value=Initialization.Failed("数据未被清空。无法打开记忆库：${e.message?.take(160)}")
                ready.completeExceptionally(e)
            }
        }
    }
    suspend fun awaitReady() { ready.await() }
    companion object {
        // The factory always passes applicationContext; never retain an Activity.
        @Suppress("StaticFieldLeak") @Volatile private var instance: AppContainer?=null
        fun from(context: Context): AppContainer = instance ?: synchronized(this) {
            instance ?: AppContainer(context.applicationContext).also { instance=it;it.initialize() }
        }
    }
}
