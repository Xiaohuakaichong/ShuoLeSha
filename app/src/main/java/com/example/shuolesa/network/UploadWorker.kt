package com.example.shuolesa.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shuolesa.AppContainer
import kotlinx.coroutines.CancellationException

/** Every persisted job has its own KEEP work identity. Immediate and durable runners share claims. */
class UploadWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val app=AppContainer.from(applicationContext)
        app.awaitReady()
        val id=inputData.getString("job_id")
        if(id==null) { app.processing.requestProcessing();return Result.success() }
        // v4.1.1: a retry keeps this unique work enqueued; any other end lets the coordinator enqueue it again.
        var retry=false
        try {
            app.repository.dao.wakeNetworkJob(id)
            val ok=app.processing.runOne(id)
            val job=app.repository.dao.job(id)
            val state=job?.state
            return when {
                state==null || state=="COMPLETE" -> Result.success()
                // v4.1-a: 429/5xx are re-scheduled by ProcessingCoordinator with Retry-After ("memory-retry:<id>").
                state=="WAITING_NETWORK" && ProcessingReason.code(job?.error)==ProcessingReason.RATE_LIMITED -> Result.success()
                state=="WAITING_NETWORK" -> { retry=true;Result.retry() }
                // WAITING_CONFIG is woken on save / foreground / network change (rate limited), not by backoff.
                state=="WAITING_CONFIG" -> Result.success()
                state=="WAITING_DEPENDENCY" -> Result.success()
                // Left PENDING because outbound consent is missing or a Retry-After window is still open.
                state=="PENDING" && ok -> Result.success()
                (job?.attempts ?: 0)>=5 -> Result.failure()
                !ok -> { retry=true;Result.retry() }
                else -> Result.success()
            }
        } catch(e: CancellationException) {
            retry=true // stopped by the system; WorkManager reschedules the same work
            throw e
        } finally {
            if(!retry) app.processing.onDurableWorkFinished(id)
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
