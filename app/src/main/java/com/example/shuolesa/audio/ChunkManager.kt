package com.example.shuolesa.audio

import android.content.Context
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.repository.MemoryRepository
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** PCM samples, not callback count or wall time, determine duration. */
class ChunkManager(private val context: Context,private val repository: MemoryRepository,
    val sessionId: String,private val mode: String,private val bitrate: Int=24,private val format: String="wav") {
    private var wav: WavWriter?=null
    private var aac: AacWriter?=null
    private var file: File?=null
    private var recordId=0L
    private var samples=0L
    private var completedSamples=0L
    private var index=0
    private var day=LocalDate.now()
    private var checkpointAt=0L
    val durationMs get()=(completedSamples+samples)*1000/AudioRecorder.SAMPLE_RATE
    val savedChunks get()=index

    private suspend fun open() {
        val now=System.currentTimeMillis()
        day=LocalDate.now()
        val dir=File(context.filesDir,"recordings").apply { check(mkdirs() || isDirectory) }
        val compressed=mode=="lifelog" || format=="aac_64k"
        val ext=if(compressed) "aac" else "wav"
        val target=File(dir,"${now}_${sessionId}_chunk${String.format(Locale.ROOT,"%03d",index)}.$ext.part")
        recordId=repository.registerChunk(AudioRecordEntity(sessionId=sessionId,chunkIndex=index,
            filePath=target.path,durationMs=0,fileSizeBytes=0,createdAt=now,status="WRITING",
            recordingMode=mode,audioFormat=if(compressed) "aac_${if(mode=="meeting") 64 else bitrate}k" else "wav"))
        file=target; samples=0; checkpointAt=now
        if(compressed) aac=AacWriter(target,bitRate=if(mode=="meeting") 64000 else bitrate.coerceIn(16,32)*1000)
        else wav=WavWriter(target)
    }
    suspend fun onPcmFrame(pcm: ShortArray,count: Int) {
        if(file!=null && day!=LocalDate.now()) closeChunk()
        var offset=0
        while(offset<count) {
            if(file==null) open()
            val n=minOf(count-offset,(MAX_SAMPLES-samples).toInt())
            wav?.write(pcm,n,offset); aac?.write(pcm,n,offset)
            samples+=n; offset+=n
            if(samples>=MAX_SAMPLES) closeChunk()
        }
        if(file!=null && System.currentTimeMillis()-checkpointAt>=30_000) {
            repository.audio.sealChunk(recordId,samples*1000/16000,file!!.length(),"WRITING")
            checkpointAt=System.currentTimeMillis()
        }
    }
    suspend fun closeChunk() {
        val partial=file ?: return
        var failure: Exception?=null
        try { wav?.finish(); aac?.finish() } catch(e: Exception) { failure=e }
        val target=File(partial.path.removeSuffix(".part"))
        if(!partial.renameTo(target)) throw java.io.IOException("录音封存失败，原件已保留")
        repository.audio.updateFilePath(recordId,target.path)
        repository.sealChunk(recordId,samples*1000/16000,target.length(),mode=="lifelog")
        completedSamples+=samples; samples=0; index++
        file=null; wav=null; aac=null
        failure?.let { throw it }
    }
    companion object { const val MAX_SAMPLES=16000L*300 }
}
