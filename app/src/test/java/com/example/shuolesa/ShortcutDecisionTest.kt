package com.example.shuolesa

import com.example.shuolesa.service.ShortcutDecision
import com.example.shuolesa.service.ShortcutDecision.MainIntent
import com.example.shuolesa.service.ShortcutDecision.TileTap
import com.example.shuolesa.service.ShortcutDecision.Trampoline
import com.example.shuolesa.service.ShortcutTokens
import com.example.shuolesa.service.TileConsentCache
import org.junit.Assert.*
import org.junit.Test

/** v4.1-a.2/a.3: shortcut decisions, exported-entry hardening, tile cache. */
class ShortcutDecisionTest {
    @Test fun tileNeverRecordsOnUnknownOrFalseConsent() {
        assertEquals(TileTap.OPEN_TRAMPOLINE, ShortcutDecision.onTileTap(false, null))
        assertEquals(TileTap.OPEN_TRAMPOLINE, ShortcutDecision.onTileTap(false, false))
        assertEquals(TileTap.START_RECORDING, ShortcutDecision.onTileTap(false, true))
        for (c in listOf(null, false, true)) assertEquals(TileTap.STOP, ShortcutDecision.onTileTap(true, c))
    }

    @Test fun trampolineUsesRealConsentAndForegroundState() {
        assertEquals(Trampoline.SHOW_CONSENT, ShortcutDecision.onTrampoline(false, true, false))
        assertEquals(Trampoline.SHOW_CONSENT, ShortcutDecision.onTrampoline(false, true, true))
        assertEquals(Trampoline.ASK_MICROPHONE, ShortcutDecision.onTrampoline(true, false, false))
        // background: start, nothing to close (no UI); foreground: keep the user's screen + "已开始录音"
        assertEquals(Trampoline.START_SILENTLY, ShortcutDecision.onTrampoline(true, true, false))
        assertEquals(Trampoline.START_AND_NOTIFY_APP, ShortcutDecision.onTrampoline(true, true, true))
    }

    /** qa a.2 P1: an external `am start` with any extras gets NONE (open normally, never record). */
    @Test fun exportedMainActivityIgnoresUntrustedRequests() {
        for (r in MainIntent.entries) assertEquals(MainIntent.NONE, ShortcutDecision.onMainIntent(r, tokenValid = false))
        assertEquals(MainIntent.NONE, ShortcutDecision.onMainIntent(null, true))
        assertEquals(MainIntent.SHOW_CONSENT, ShortcutDecision.onMainIntent(MainIntent.SHOW_CONSENT, true))
        assertEquals(MainIntent.SHOW_STARTED, ShortcutDecision.onMainIntent(MainIntent.SHOW_STARTED, true))
        // MainIntent has no "start recording" request at all: MainActivity can never auto-record.
        assertFalse(MainIntent.entries.any { it.name.contains("START_RECORD") })
    }

    @Test fun tokensAreOneTimeAndExpire() {
        var t = 0L
        val tokens = ShortcutTokens(now = { t }, ttlMs = 30_000)
        val a = tokens.issue()
        assertFalse(tokens.consume(null)); assertFalse(tokens.consume("")); assertFalse(tokens.consume("forged-token"))
        assertTrue(tokens.consume(a)); assertFalse(tokens.consume(a)) // single use
        val b = tokens.issue(); t = 30_001
        assertFalse(tokens.consume(b)) // expired
    }

    @Test fun revokeClearsTileCache() {
        TileConsentCache.value = true
        assertEquals(TileTap.START_RECORDING, ShortcutDecision.onTileTap(false, TileConsentCache.value))
        TileConsentCache.clear()
        assertNull(TileConsentCache.value)
        assertEquals(TileTap.OPEN_TRAMPOLINE, ShortcutDecision.onTileTap(false, TileConsentCache.value))
    }
}
