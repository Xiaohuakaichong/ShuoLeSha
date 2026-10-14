package com.example.shuolesa.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shuolesa.AppContainer

/** Every persisted job has its own KEEP work identity. Immediate and durable runners share claims. */
class UploadWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val app=AppContainer.from(applicationContext)
        app.awaitReady()
        val id=inputData.getString("job_id")
        if(id==null) { app.processing.requestProcessing();return Result.success() }
        app.repository.dao.wakeNetworkJob(id)
        val ok=app.processing.runOne(id)
        val state=app.repository.dao.job(id)?.state
        return when {
            state==null || state=="COMPLETE" -> Result.success()
            state in listOf("WAITING_CONFIG","WAITING_NETWORK") -> if(state=="WAITING_NETWORK") Result.retry() else Result.success()
            state=="WAITING_DEPENDENCY" -> Result.success()
            (app.repository.dao.job(id)?.attempts ?: 0) >=5 -> Result.failure()
            !ok -> Result.retry()
            else -> Result.success()
        }
    }
    companion object {
        const val WORK_NAME="audio_upload_work"
        fun enqueueOrRunNow(context: Context) { AppContainer.from(context).processing.requestProcessing() }
        suspend fun processPendingUploadsManual(context: Context): Pair<Int,Int> {
            val app=AppContainer.from(context);app.awaitReady();app.repository.retry()
            var success=0;var failure=0
            for(job in app.repository.dao.pendingJobs()) if(app.processing.runOne(job.id)) success++ else failure++
            app.processing.requestProcessing();return success to failure
        }
    }
}
