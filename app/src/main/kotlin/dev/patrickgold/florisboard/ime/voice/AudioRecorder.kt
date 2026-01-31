/*
 * Copyright (C) 2025 The VoiceFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException


/**
 * Audio recorder wrapper for recording voice input.
 *
 * Uses MP4/AAC format by default (good compression, native support).
 * Falls back to 3GP/AMR if MP4 encoding fails on specific devices.
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
        private const val CHANNELS = 1
        private const val FILE_PREFIX = "voice_recording_"
        private const val MP4_EXTENSION = ".mp4"
        private const val FALLBACK_EXTENSION = ".3gp"
        private const val MIN_RECORDING_MS = 500L // Minimum recording duration
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var useFallbackFormat = false
    private var recordingStartTime: Long = 0L

    /**
     * The format of the current recording.
     * Returns "mp3" for MP4/AAC (OpenRouter accepts both), "wav" for fallback.
     */
    val audioFormat: String
        get() = if (useFallbackFormat) "wav" else "mp3"

    /**
     * Whether the recorder is currently recording.
     */
    val recording: Boolean
        get() = isRecording

    /**
     * Starts recording audio.
     *
     * @return true if recording started successfully, false otherwise
     */
    fun start(): Boolean {
        if (isRecording) {
            return false
        }

        // Clean up any previous recording
        cleanup()

        return try {
            startWithFormat(useFallback = false)
        } catch (e: Exception) {
            // Try fallback format
            cleanup()
            try {
                useFallbackFormat = true
                startWithFormat(useFallback = true)
            } catch (e2: Exception) {
                cleanup()
                false
            }
        }
    }

    private fun startWithFormat(useFallback: Boolean): Boolean {
        val extension = if (useFallback) FALLBACK_EXTENSION else MP4_EXTENSION
        outputFile = File(context.cacheDir, "$FILE_PREFIX${System.currentTimeMillis()}$extension")

        mediaRecorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)

            if (useFallback) {
                // Fallback: 3GP container with AMR encoding (widely supported)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            } else {
                // Primary: MP4 container with AAC encoding
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioChannels(CHANNELS)
            }

            setOutputFile(outputFile!!.absolutePath)

            try {
                prepare()
                start()
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
            } catch (e: IOException) {
                release()
                throw e
            }
        }

        return true
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    /**
     * Stops recording and returns the recorded audio file.
     * If recording duration is too short, waits for minimum duration.
     *
     * @return The recorded audio file, or null if recording failed
     */
    fun stop(): File? {
        if (!isRecording) {
            return null
        }

        // Wait for minimum recording duration to avoid MediaRecorder errors
        val elapsed = System.currentTimeMillis() - recordingStartTime
        if (elapsed < MIN_RECORDING_MS) {
            try {
                Thread.sleep(MIN_RECORDING_MS - elapsed)
            } catch (e: InterruptedException) {
                // Continue with stop
            }
        }

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            outputFile
        } catch (e: Exception) {
            cleanup()
            null
        }
    }

    /**
     * Cancels the current recording and deletes the file.
     */
    fun cancel() {
        cleanup()
    }

    /**
     * Cleans up resources and deletes temporary files.
     */
    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                if (isRecording) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        mediaRecorder = null
        isRecording = false

        outputFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        outputFile = null
    }

    /**
     * Deletes the given audio file immediately.
     */
    fun deleteFile(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // Ignore deletion errors
        }
    }

    /**
     * Cleans up old temporary audio files in the cache directory.
     * Files older than the specified age (in milliseconds) will be deleted.
     *
     * @param maxAgeMs Maximum age of files to keep (default: 5 minutes)
     */
    fun cleanupOldFiles(maxAgeMs: Long = 5 * 60 * 1000) {
        try {
            val now = System.currentTimeMillis()
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(FILE_PREFIX) &&
                    (now - file.lastModified()) > maxAgeMs
                ) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
