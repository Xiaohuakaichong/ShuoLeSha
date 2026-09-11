package com.example.shuolesa.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 全局应用级 CoroutineScope，生命周期与进程相同，不会因为 Service 或 Activity 销毁而被中断。
 */
object AppScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default
}
