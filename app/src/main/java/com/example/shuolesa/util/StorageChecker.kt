package com.example.shuolesa.util

import android.content.Context
import android.os.Environment
import android.os.StatFs

class StorageChecker(private val context: Context) {

    /**
     * Check if there is at least [minMB] megabytes of free storage.
     */
    fun hasEnoughStorage(minMB: Long = 100): Boolean {
        val path = context.filesDir.absolutePath
        val stat = StatFs(path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val availableMB = availableBytes / (1024 * 1024)
        return availableMB >= minMB
    }

    /**
     * Get available storage in MB.
     */
    fun getAvailableStorageMB(): Long {
        val path = context.filesDir.absolutePath
        val stat = StatFs(path)
        return (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
    }
}
