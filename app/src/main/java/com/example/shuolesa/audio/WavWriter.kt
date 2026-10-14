package com.example.shuolesa.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriter(val file: File) {
    private val stream=FileOutputStream(file)
    private var closed=false
    init { stream.write(ByteArray(HEADER_SIZE)) }
    fun write(pcm: ShortArray,count: Int,offset: Int=0) {
        check(!closed)
        val bytes=ByteBuffer.allocate(count*2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(pcm,offset,count)
        stream.write(bytes.array())
    }
    fun finish(): Long {
        if(!closed) { closed=true; stream.flush(); stream.close(); repair(file) }
        return file.length()
    }
    companion object {
        const val SAMPLE_RATE=16000
        const val CHANNELS=1
        const val BITS_PER_SAMPLE=16
        const val HEADER_SIZE=44
        fun repair(file: File): Long {
            val payload=(file.length()-HEADER_SIZE).coerceAtLeast(0)/2*2
            RandomAccessFile(file,"rw").use { output ->
                output.setLength(payload+HEADER_SIZE)
                val header=ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray()).putInt((payload+36).toInt()).put("WAVEfmt ".toByteArray())
                header.putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE*2)
                header.putShort(2).putShort(16).put("data".toByteArray()).putInt(payload.toInt())
                output.seek(0); output.write(header.array())
            }
            return payload*1000/(SAMPLE_RATE*2)
        }
    }
}
