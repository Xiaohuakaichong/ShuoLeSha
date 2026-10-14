package com.example.shuolesa.audio

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

interface PausePlayback {suspend fun pausePlayback()}

/** Also covers recording started from the tile or volume keys while a source is open. */
object AudioPlaybackGate {
    private val players=CopyOnWriteArrayList<WeakReference<PausePlayback>>()
    fun register(player:PausePlayback) {players.add(WeakReference(player))}
    fun unregister(player:PausePlayback) {players.removeAll {it.get()==null || it.get()===player}}
    suspend fun pauseAll() {players.forEach {ref->ref.get()?.pausePlayback()};players.removeAll {it.get()==null}}
}
