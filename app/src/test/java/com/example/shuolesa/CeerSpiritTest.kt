package com.example.shuolesa

import androidx.compose.ui.geometry.Offset
import com.example.shuolesa.ui.components.CeerPose
import com.example.shuolesa.ui.components.Spirit
import org.junit.Assert.*
import org.junit.Test

/** v5.1: the ink spirit's motion is plain math, so it is checked without Compose. */
class CeerSpiritTest {
    private fun run(s: Spirit, seconds: Float, pose: CeerPose, look: Offset? = null, energy: Float = 0f, onFrame: (Spirit) -> Unit = {}) {
        repeat((seconds / 0.016f).toInt()) { s.step(0.016f, pose, energy, look); onFrame(s) }
    }

    @Test fun headTurnsTowardTheFingerAndBackToItself() {
        val s = Spirit()
        run(s, 1.5f, CeerPose.IDLE, Offset(1f, -0.5f))
        assertTrue("yaw ${s.yaw}", s.yaw > 0.9f)
        assertTrue("pitch ${s.pitch}", s.pitch < -0.4f)
    }

    @Test fun blinksAndOpensAgain() {
        val s = Spirit()
        var lowest = 1f
        run(s, 10f, CeerPose.IDLE) { lowest = minOf(lowest, it.open) }
        assertTrue("never blinked, lowest $lowest", lowest < 0.2f)
        run(s, 0.5f, CeerPose.IDLE, Offset.Zero)
        assertTrue("eyes stay open between blinks", s.lid > 0.99f)
    }

    @Test fun sleepClosesTheEyesAndNeverBlinks() {
        val s = Spirit()
        s.settle(CeerPose.SLEEP, 0f)
        assertEquals(0.05f, s.open, 0.001f)
        assertFalse(s.happy)
        run(s, 5f, CeerPose.SLEEP) { assertTrue(it.open <= 0.06f) }
    }

    @Test fun pausedKeepsHalfClosedEyes() {
        val s = Spirit()
        s.settle(CeerPose.PAUSED, 0f)
        assertEquals(0.45f, s.lid, 0.001f)
        run(s, 3f, CeerPose.PAUSED) { assertTrue(it.lid in 0.44f..0.46f) }
    }

    @Test fun successIsHappyAndHops() {
        val s = Spirit()
        run(s, 1f, CeerPose.SUCCESS)
        assertTrue(s.happy)
        var top = 0f
        run(s, 1f, CeerPose.SUCCESS) { top = maxOf(top, it.hop) }
        assertTrue("hop $top", top > 0.9f)
    }

    @Test fun aTapMakesItHappyForUnderASecondButNotWhileAsleepOrWorried() {
        val s = Spirit()
        run(s, 0.1f, CeerPose.IDLE)
        s.pokeAt = s.t
        run(s, 0.1f, CeerPose.IDLE)
        assertTrue(s.happy)
        run(s, 1f, CeerPose.IDLE)
        assertFalse(s.happy)
        for (pose in listOf(CeerPose.SLEEP, CeerPose.ERROR)) {
            s.pokeAt = s.t
            run(s, 0.1f, pose)
            assertFalse(pose.name, s.happy)
        }
    }

    @Test fun errorLooksAwayAndWorries() {
        val s = Spirit()
        s.settle(CeerPose.ERROR, 0f)
        assertTrue(s.yaw < -0.4f)
        assertEquals(0f, s.mouth, 0f)
    }

    @Test fun speakingFollowsTheLoudnessWhenThereIsOne() {
        val s = Spirit()
        run(s, 0.5f, CeerPose.SPEAKING, energy = 0.5f)
        assertEquals(0.7f, s.mouth, 0.001f)
        run(s, 0.5f, CeerPose.SPEAKING, energy = 0f)
        assertTrue("falls back to a lively mouth", s.mouth in 0.1f..1f)
    }

    @Test fun listeningStretchesUp() {
        val s = Spirit()
        run(s, 1.5f, CeerPose.LISTENING, energy = 1f)
        assertTrue(s.stretch > 1.07f)
        assertTrue(s.tilt < -5f)
    }
}
