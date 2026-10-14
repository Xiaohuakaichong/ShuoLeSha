package com.example.shuolesa

import android.app.Application
import android.media.MediaCodec
import com.example.shuolesa.audio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class AacDeadlineTest {
    private class Encoder(val input:Boolean,val output:Boolean):AacEncoderPort {
        var released=0;var stopped=0;var eos=false
        override fun dequeueInputBuffer(timeoutUs:Long)=if(input) 0 else -1
        override fun getInputBuffer(index:Int)=ByteBuffer.allocate(4096)
        override fun queueInputBuffer(index:Int,offset:Int,size:Int,timeUs:Long,flags:Int) {eos=flags==MediaCodec.BUFFER_FLAG_END_OF_STREAM}
        override fun dequeueOutputBuffer(info:MediaCodec.BufferInfo,timeoutUs:Long):Int {
            if(output && eos) {info.set(0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);return 0};return -1
        }
        override fun getOutputBuffer(index:Int)=ByteBuffer.allocate(0)
        override fun releaseOutputBuffer(index:Int,render:Boolean) {}
        override fun stop() {stopped++}
        override fun release() {released++}
    }
    @Test fun missingInputOrOutputCannotWaitForeverAndAlwaysRelease() {
        listOf(false to false,true to false).forEach { (input,output)->
            val file=File.createTempFile("eos-test",".aac");val codec=Encoder(input,output);var time=0L
            val writer=AacWriter(file,codecFactory={codec},now={time+=100_000_000;time})
            try {assertThrows(IOException::class.java) {writer.finish()};assertEquals(1,codec.released);assertEquals(1,codec.stopped)
                writer.finish();assertEquals(1,codec.released)
            } finally {file.delete()}
        }
    }
    @Test fun successfulEosClosesExactlyOnce() {
        val file=File.createTempFile("eos-test",".aac");val codec=Encoder(true,true);var time=0L
        val writer=AacWriter(file,codecFactory={codec},now={time+=100_000_000;time})
        try {writer.finish();writer.finish();assertTrue(codec.eos);assertEquals(1,codec.released)} finally {file.delete()}
    }
}
