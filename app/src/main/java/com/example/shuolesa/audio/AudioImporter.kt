package com.example.shuolesa.audio

import android.content.Context
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.File

/** Original is retained; resumable WAV slices are temporary ASR inputs. */
class AudioImporter(private val context: Context,private val repository: MemoryRepository) {
    suspend fun prepare(sessionId: String) {
        val session=repository.dao.session(sessionId) ?: return
        val original=File(session.sourcePath ?: error("导入原件不可用"))
        val existing=repository.audio.chunks(sessionId).filter { it.status!="WRITING" }.sortedBy { it.chunkIndex }
        val skipped=existing.mapNotNull { it.sourceEndMs }.maxOrNull()?.times(16) ?: 0L
        val dir=File(context.filesDir,"import_chunks").apply { check(mkdirs() || isDirectory) }
        var total=0L;var samples=0L;var index=(existing.maxOfOrNull { it.chunkIndex } ?: -1)+1
        var writer: WavWriter?=null;var file: File?=null;var id=0L;var startSamples=0L
        fun seal() {
            val target=file ?: return
            writer?.finish()
            runBlocking {
                repository.audio.setSourceEnd(id,(startSamples+samples)*1000/16000)
                repository.sealChunk(id,samples*1000/16000,target.length(),true)
            }
            writer=null;file=null;samples=0;index++
        }
        try {
            runInterruptible(Dispatchers.IO) {
                AudioDecoder.decode(original) { pcm,count ->
                    var offset=0
                    if(total<skipped) { val skip=minOf(count.toLong(),skipped-total).toInt();offset=skip;total+=skip }
                    while(offset<count) {
                        if(writer==null) {
                            if(context.filesDir.usableSpace<25L*1024*1024) error("存储空间不足，原件与已完成分片均已保留")
                            startSamples=total
                            val target=File(dir,"${sessionId}_$index.wav")
                            id=runBlocking { repository.registerChunk(AudioRecordEntity(sessionId=sessionId,chunkIndex=index,
                                filePath=target.path,durationMs=0,fileSizeBytes=0,createdAt=session.startedAt,status="WRITING",
                                recordingMode=session.mode,audioFormat="wav",sourceStartMs=total*1000/16000)) }
                            file=target;writer=WavWriter(target)
                        }
                        val n=minOf(count-offset,(ChunkManager.MAX_SAMPLES-samples).toInt())
                        writer!!.write(pcm,n,offset);offset+=n;samples+=n;total+=n
                        if(samples>=ChunkManager.MAX_SAMPLES) seal()
                    }
                }
            }
        } finally { seal() }
        repository.dao.updateCapture(sessionId,"CLOSED",session.endedAt,total*1000/16000)
        repository.dao.updateProcessing(sessionId,"TRANSCRIBING")
    }
}
