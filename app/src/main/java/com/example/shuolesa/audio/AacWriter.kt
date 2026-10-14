package com.example.shuolesa.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ADTS AAC. Every wait has a deadline, including submission and draining of EOS. */
class AacWriter internal constructor(val file: File, private val sampleRate: Int = 16000, private val bitRate: Int = 24000,
    private val codecFactory:()->AacEncoderPort={AndroidAacEncoder(sampleRate,bitRate)},private val now:()->Long=System::nanoTime) {
    private val stream = FileOutputStream(file)
    private var codec: AacEncoderPort? = null
    private var timeUs = 0L
    private var finished = false
    init {
        try {
            codec=codecFactory()
        } catch (e: Exception) {
            runCatching { codec?.release() }; stream.close()
            throw IOException("无法初始化录音编码器",e)
        }
    }
    fun write(pcm: ShortArray, count: Int, offset: Int = 0) {
        check(!finished)
        val native = codec ?: throw IOException("编码器已关闭")
        val bytes = ByteBuffer.allocate(count*2).order(ByteOrder.LITTLE_ENDIAN).apply {
            asShortBuffer().put(pcm,offset,count)
        }.array()
        var cursor=0
        val deadline=now()+2_000_000_000L
        while(cursor<bytes.size) {
            if(now()>deadline) throw IOException("录音编码超时，已保留已写入部分")
            val index=native.dequeueInputBuffer(10_000)
            if(index>=0) {
                val input=native.getInputBuffer(index) ?: throw IOException("编码输入缓冲不可用")
                input.clear()
                val size=minOf(input.remaining(),bytes.size-cursor)
                input.put(bytes,cursor,size)
                native.queueInputBuffer(index,0,size,timeUs,0)
                timeUs+=(size/2)*1_000_000L/sampleRate
                cursor+=size
            }
            drain(false,deadline)
        }
    }
    private fun drain(end: Boolean, deadline: Long): Boolean {
        val native=codec ?: return false
        val info=MediaCodec.BufferInfo()
        while(now()<deadline) {
            val index=native.dequeueOutputBuffer(info,10_000)
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER && !end) return false
            if(index>=0) {
                val buffer=native.getOutputBuffer(index)
                if(buffer!=null && info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val packet=ByteArray(info.size+7)
                    val length=packet.size
                    // MPEG-4 AAC-LC, 16kHz, mono, no CRC.
                    val frequency=when(sampleRate) { 16000->8; 44100->4; 48000->3; else->8 }
                    packet[0]=0xFF.toByte(); packet[1]=0xF1.toByte()
                    packet[2]=((1 shl 6) or (frequency shl 2)).toByte()
                    packet[3]=((1 shl 6) or (length shr 11)).toByte()
                    packet[4]=((length and 0x7FF) shr 3).toByte()
                    packet[5]=(((length and 7) shl 5) or 0x1F).toByte(); packet[6]=0xFC.toByte()
                    buffer.position(info.offset); buffer.limit(info.offset+info.size)
                    buffer.get(packet,7,info.size); stream.write(packet)
                }
                native.releaseOutputBuffer(index,false)
                if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
            }
        }
        if(end) throw IOException("编码结束等待超时，已保留可恢复音频")
        return false
    }
    fun finish(): Long {
        if(finished) return file.length()
        finished=true
        val native=codec
        try {
            if(native!=null) {
                val deadline=now()+2_000_000_000L
                var eos=false
                while(!eos && now()<deadline) {
                    val index=native.dequeueInputBuffer(10_000)
                    if(index>=0) {
                        native.queueInputBuffer(index,0,0,timeUs,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eos=true
                    } else drain(false,deadline)
                }
                if(!eos) throw IOException("无法提交编码结束信号")
                drain(true,deadline)
            }
        } finally {
            runCatching { native?.stop() }; runCatching { native?.release() }; codec=null
            stream.flush(); stream.close()
        }
        return file.length()
    }
    companion object {
        /** Discard an incomplete trailing ADTS frame after a process interruption. */
        fun recover(file: File): Long {
            var frames=0L; var offset=0L
            RandomAccessFile(file,"rw").use { input ->
                val header=ByteArray(7)
                while(offset+7<=input.length()) {
                    input.seek(offset); input.readFully(header)
                    if(header[0].toInt() and 255 != 255 || header[1].toInt() and 0xF6 != 0xF0) break
                    val length=((header[3].toInt() and 3) shl 11) or
                        ((header[4].toInt() and 255) shl 3) or ((header[5].toInt() and 0xE0) shr 5)
                    if(length<7 || offset+length>input.length()) break
                    offset+=length; frames++
                }
                input.setLength(offset)
            }
            return frames*1024*1000/16000
        }
    }
}

/** Codec boundary lets deadline/resource behavior be tested without a physical encoder. */
internal interface AacEncoderPort {
    fun dequeueInputBuffer(timeoutUs:Long):Int
    fun getInputBuffer(index:Int):ByteBuffer?
    fun queueInputBuffer(index:Int,offset:Int,size:Int,timeUs:Long,flags:Int)
    fun dequeueOutputBuffer(info:MediaCodec.BufferInfo,timeoutUs:Long):Int
    fun getOutputBuffer(index:Int):ByteBuffer?
    fun releaseOutputBuffer(index:Int,render:Boolean)
    fun stop()
    fun release()
}
internal class AndroidAacEncoder(rate:Int,bitrate:Int):AacEncoderPort {
    private val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    init {
        try {
            codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,rate,1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE,bitrate)
            },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch(e:Exception) {runCatching {codec.release()};throw e}
    }
    override fun dequeueInputBuffer(timeoutUs:Long)=codec.dequeueInputBuffer(timeoutUs)
    override fun getInputBuffer(index:Int)=codec.getInputBuffer(index)
    override fun queueInputBuffer(index:Int,offset:Int,size:Int,timeUs:Long,flags:Int)=codec.queueInputBuffer(index,offset,size,timeUs,flags)
    override fun dequeueOutputBuffer(info:MediaCodec.BufferInfo,timeoutUs:Long)=codec.dequeueOutputBuffer(info,timeoutUs)
    override fun getOutputBuffer(index:Int)=codec.getOutputBuffer(index)
    override fun releaseOutputBuffer(index:Int,render:Boolean)=codec.releaseOutputBuffer(index,render)
    override fun stop()=codec.stop()
    override fun release()=codec.release()
}
