package com.example.shuolesa

import com.example.shuolesa.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecoveryTest {
    @Test fun interruptedWavRebuildsHeaderAndDuration() {
        val file=File.createTempFile("interrupted",".wav")
        try {file.writeBytes(ByteArray(44+32001));assertEquals(1000,WavWriter.repair(file));assertEquals(32044,file.length())
            val header=ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN);assertEquals("RIFF",String(file.readBytes().take(4).toByteArray()));assertEquals(32000,header.getInt(40))
        } finally {file.delete()}
    }
    @Test fun truncatedAdtsTailIsDiscarded() {
        val file=File.createTempFile("interrupted",".aac")
        try {val frame=byteArrayOf(0xFF.toByte(),0xF1.toByte(),0x60,0x40,0x01,0x5F,0xFC.toByte(),1,2,3)
            file.writeBytes(frame+frame+frame.take(8));assertEquals(128,AacWriter.recover(file));assertEquals(20,file.length())
        } finally {file.delete()}
    }
    @Test fun wavFinishIsIdempotentAndUsesActualSamples() {
        val file=File.createTempFile("record",".wav")
        try {val writer=WavWriter(file);writer.write(ShortArray(16000),16000);assertEquals(32044,writer.finish());assertEquals(32044,writer.finish());assertEquals(1000,WavWriter.repair(file))}
        finally {file.delete()}
    }
}
