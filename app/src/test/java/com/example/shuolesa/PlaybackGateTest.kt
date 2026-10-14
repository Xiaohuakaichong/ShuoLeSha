package com.example.shuolesa

import com.example.shuolesa.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackGateTest {
    @Test fun everyActivePlayerPausesBeforeCaptureAndUnregisteredPlayersAreSkipped()=runBlocking {
        var first=0;var second=0
        val a=object:PausePlayback {override suspend fun pausePlayback() {first++}}
        val b=object:PausePlayback {override suspend fun pausePlayback() {second++}}
        AudioPlaybackGate.register(a);AudioPlaybackGate.register(b)
        try {AudioPlaybackGate.pauseAll();assertEquals(1,first);assertEquals(1,second)
            AudioPlaybackGate.unregister(a);AudioPlaybackGate.pauseAll();assertEquals(1,first);assertEquals(2,second)
        } finally {AudioPlaybackGate.unregister(a);AudioPlaybackGate.unregister(b)}
    }
}
