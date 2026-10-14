package com.example.shuolesa.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Stream system-supported audio into 16kHz mono PCM; never loads a long import into RAM. */
object AudioDecoder {
    fun decode(file: File,onPcm: (ShortArray,Int)->Unit) {
        val extractor=MediaExtractor()
        var decoder: MediaCodec?=null
        try {
            extractor.setDataSource(file.path)
            val track=(0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
            } ?: throw IOException("文件中没有可读取的音轨")
            extractor.selectTrack(track)
            val input=extractor.getTrackFormat(track)
            decoder=MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(input,null,null,0); decoder.start()
            var rate=input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels=input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding=AudioFormat.ENCODING_PCM_16BIT
            var inputEnded=false; var outputEnded=false
            var stalledAt=System.nanoTime()
            var accumulator=0L
            val pcm=ShortArray(4096); var count=0
            val info=MediaCodec.BufferInfo()
            while(!outputEnded) {
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                if(System.nanoTime()-stalledAt>15_000_000_000L) throw IOException("音频解码超时")
                if(!inputEnded) {
                    val index=decoder.dequeueInputBuffer(10_000)
                    if(index>=0) {
                        val buffer=decoder.getInputBuffer(index)!!
                        buffer.clear()
                        val size=extractor.readSampleData(buffer,0)
                        if(size<0) {
                            decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded=true
                        } else {
                            decoder.queueInputBuffer(index,0,size,extractor.sampleTime,0); extractor.advance()
                        }
                    }
                }
                val index=decoder.dequeueOutputBuffer(info,10_000)
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format=decoder.outputFormat
                    rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if(format.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding=format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else if(index>=0) {
                    stalledAt=System.nanoTime()
                    val buffer=decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset); buffer.limit(info.offset+info.size)
                    val bytes=if(encoding==AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                    while(buffer.remaining()>=channels*bytes) {
                        var sum=0L
                        repeat(channels) { sum+=if(bytes==4) (buffer.float.coerceIn(-1f,1f)*32767).toLong() else buffer.short.toLong() }
                        accumulator+=16000
                        while(accumulator>=rate) {
                            pcm[count++]=(sum/channels).toShort(); accumulator-=rate
                            if(count==pcm.size) { onPcm(pcm,count); count=0 }
                        }
                    }
                    outputEnded=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index,false)
                }
            }
            if(count>0) onPcm(pcm,count)
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }; extractor.release()
        }
    }
    fun toWav(source: File,target: File): File {
        val writer=WavWriter(target)
        try { decode(source) { pcm,count -> writer.write(pcm,count) } } finally { writer.finish() }
        return target
    }
}
