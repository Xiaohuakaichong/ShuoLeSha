package com.example.shuolesa.util

import android.app.Application
import android.os.Build
import android.util.Log
import com.example.shuolesa.BuildConfig

/** Startup metadata only. Do not collect transcripts, API bodies, keys or system logcat. */
object AppLogger {
    fun init(app:Application) {Log.i("ShuoLeSha","version=${BuildConfig.VERSION_NAME} sdk=${Build.VERSION.SDK_INT}")}
}
