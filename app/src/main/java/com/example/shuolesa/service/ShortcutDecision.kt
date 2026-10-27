package com.example.shuolesa.service

/**
 * Pure decision logic for the tile / volume-key shortcut (unit tested in ShortcutDecisionTest).
 * Rule: nothing records on an unknown or cached-false consent; the real stored value decides in the trampoline.
 */
object ShortcutDecision {
    enum class TileTap { STOP, START_RECORDING, OPEN_TRAMPOLINE }
    /** v4.1-a.3: what ShortcutTrampolineActivity does after reading the real stored consent. */
    enum class Trampoline { START_SILENTLY, START_AND_NOTIFY_APP, ASK_MICROPHONE, SHOW_CONSENT }
    /** v4.1-a.3: what MainActivity does with an incoming intent. */
    enum class MainIntent { NONE, SHOW_CONSENT, ASK_MICROPHONE, SHOW_STARTED }

    /** [cachedConsent] is null on cold start / after revoke -> treated as not consented. */
    fun onTileTap(recording: Boolean, cachedConsent: Boolean?): TileTap = when {
        recording -> TileTap.STOP
        cachedConsent == true -> TileTap.START_RECORDING
        else -> TileTap.OPEN_TRAMPOLINE
    }

    /**
     * [appVisible]: MainActivity was already started (in the foreground) when the shortcut fired.
     * Background -> start and close (the trampoline has no UI, nothing to close). Foreground -> keep the
     * user's screen and tell MainActivity to show "已开始录音 · 查看".
     */
    fun onTrampoline(storedConsent: Boolean, micGranted: Boolean, appVisible: Boolean): Trampoline = when {
        !storedConsent -> Trampoline.SHOW_CONSENT
        !micGranted -> Trampoline.ASK_MICROPHONE
        appVisible -> Trampoline.START_AND_NOTIFY_APP
        else -> Trampoline.START_SILENTLY
    }

    /**
     * MainActivity is exported. It only acts on [requested] when [tokenValid] (a one-time in-process token
     * issued by the non-exported trampoline). Anything from outside -> NONE: open normally, never record.
     */
    fun onMainIntent(requested: MainIntent?, tokenValid: Boolean): MainIntent =
        if (requested == null || !tokenValid) MainIntent.NONE else requested
}

/**
 * v4.1-a.3: one-time, in-memory tokens. Only code inside our process (the trampoline, the capture service)
 * can issue them, so an external `am start` / third-party Intent can never present a valid one.
 */
class ShortcutTokens(private val now: () -> Long = System::currentTimeMillis, private val ttlMs: Long = 30_000) {
    private val issued = HashMap<String, Long>()
    @Synchronized fun issue(): String {
        val token = java.util.UUID.randomUUID().toString()
        issued[token] = now() + ttlMs
        return token
    }
    /** Valid once, within the TTL. */
    @Synchronized fun consume(token: String?): Boolean {
        val expiry = issued.remove(token ?: return false) ?: return false
        issued.entries.removeAll { it.value < now() }
        return expiry >= now()
    }
    companion object { val shared = ShortcutTokens() }
}

/** v4.1-a.3: process-wide tile consent cache. null = unknown (treated as not consented). Cleared on revoke. */
object TileConsentCache {
    @Volatile var value: Boolean? = null
    fun clear() { value = null }
}
