package com.example.shuolesa.audio

import android.content.Context
import android.util.Log
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages 5-minute audio file chunking.
 * Handles file rotation without interrupting the encoder.
 */
class ChunkManager(
    private val context: Context,
    private val encoder: OpusEncoder,
    private val repository: AudioRepository,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val TAG = "ChunkManager"
        const val CHUNK_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        const val FRAME_DURATION_MS = 60L // 60ms per Opus frame
    }

    private val sessionId = UUID.randomUUID().toString().take(8)
    private var chunkIndex = 0
    private var currentFile: File? = null
    private var currentOutputStream: FileOutputStream? = null
    private var chunkStartTime = 0L
    private var frameCount = 0L
    private var totalRecordingStartTime = 0L

    val currentChunkIndex: Int get() = chunkIndex
    val elapsedMs: Long get() {
        return if (totalRecordingStartTime > 0) {
            System.currentTimeMillis() - totalRecordingStartTime
        } else 0
    }

    /**
     * Start a new recording session. Opens the first chunk file.
     */
    fun startSession(): FileOutputStream {
        totalRecordingStartTime = System.currentTimeMillis()
        return openNewChunk()
    }

    /**
     * Called after each PCM frame is encoded.
     * Checks if we need to rotate to a new chunk file.
     */
    fun onFrameEncoded(): FileOutputStream? {
        frameCount++
        val elapsedInChunk = frameCount * FRAME_DURATION_MS

        if (elapsedInChunk >= CHUNK_DURATION_MS) {
            // Time to rotate
            return rotateChunk()
        }
        return null
    }

    /**
     * Rotate to a new chunk file. Returns the new output stream.
     */
    private fun rotateChunk(): FileOutputStream {
        val oldFile = currentFile
        val oldStartTime = chunkStartTime
        val durationMs = frameCount * FRAME_DURATION_MS

        // Switch encoder output (flushes old stream)
        val newStream = openNewChunk()
        val bytesWritten = encoder.switchOutput(newStream)

        // Close old stream
        currentOutputStream?.close()

        // Register old chunk in database
        if (oldFile != null) {
            registerChunk(oldFile, chunkIndex - 1, oldStartTime, durationMs, bytesWritten)
        }

        return newStream
    }

    /**
     * Open a new chunk file.
     */
    private fun openNewChunk(): FileOutputStream {
        val audioDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val fileName = "${timestamp}_${sessionId}_chunk${String.format("%03d", chunkIndex)}.opus"
        val file = File(audioDir, fileName)

        currentFile = file
        chunkStartTime = timestamp
        frameCount = 0
        chunkIndex++

        val stream = FileOutputStream(file)
        currentOutputStream = stream
        return stream
    }

    /**
     * Finalize the current recording session.
     * Flushes and registers the last chunk.
     */
    fun finalizeSession() {
        val file = currentFile ?: return
        val durationMs = frameCount * FRAME_DURATION_MS
        val bytesWritten = encoder.stop()

        try {
            currentOutputStream?.flush()
            currentOutputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing output stream", e)
        }

        registerChunk(file, chunkIndex - 1, chunkStartTime, durationMs, bytesWritten)

        // Reset state
        currentFile = null
        currentOutputStream = null
        totalRecordingStartTime = 0
    }

    private fun registerChunk(file: File, index: Int, startTime: Long, durationMs: Long, sizeBytes: Long) {
        val fileSize = if (file.exists()) file.length() else sizeBytes
        val record = AudioRecordEntity(
            sessionId = sessionId,
            chunkIndex = index,
            filePath = file.absolutePath,
            durationMs = durationMs,
            fileSizeBytes = fileSize,
            createdAt = startTime,
        )
        scope.launch {
            try {
                repository.insertRecord(record)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register chunk in DB", e)
            }
        }
    }
}
