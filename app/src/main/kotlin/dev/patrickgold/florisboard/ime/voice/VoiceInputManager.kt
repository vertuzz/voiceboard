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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Central manager for voice input functionality.
 *
 * Orchestrates recording, transcription via OpenRouter API, and text insertion.
 * Provides state flow for UI observation.
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val MAX_DURATION_MS = 600_000L // 10 minutes
        private const val WARNING_MS = 540_000L // 9 minutes (1 minute warning)
        private const val TICK_INTERVAL_MS = 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by FlorisPreferenceStore
    
    private val audioRecorder = AudioRecorder(context)
    private var voiceClient: OpenRouterVoiceClient? = null
    
    private val _state = MutableStateFlow<VoiceRecordingState>(VoiceRecordingState.Idle)
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    private var countDownTimer: CountDownTimer? = null
    private var currentPromptMode: PromptMode = PromptMode.CLEAN
    private var hasVibratedWarning = false
    private var inputConnection: InputConnection? = null

    /**
     * The currently selected prompt mode for transcription.
     */
    val promptMode: PromptMode
        get() = currentPromptMode

    /**
     * Whether the manager is currently recording audio.
     */
    val isRecording: Boolean
        get() = _state.value is VoiceRecordingState.Recording

    /**
     * Initialize the manager. Call on app startup.
     */
    fun init() {
        // Clean up old temp files from previous sessions
        audioRecorder.cleanupOldFiles()
        
        // Load default prompt mode from preferences
        scope.launch {
            currentPromptMode = prefs.voice.defaultPromptMode.get()
        }
    }

    /**
     * Sets the input connection for text insertion.
     */
    fun setInputConnection(ic: InputConnection?) {
        inputConnection = ic
    }

    /**
     * Sets the prompt mode for the next transcription.
     */
    fun setPromptMode(mode: PromptMode) {
        currentPromptMode = mode
        scope.launch {
            prefs.voice.defaultPromptMode.set(mode)
        }
    }

    /**
     * Checks if the app has microphone permission.
     */
    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the OpenRouter API key is configured.
     */
    fun hasApiKey(): Boolean {
        return prefs.voice.apiKey.get().isNotBlank()
    }

    /**
     * Starts voice recording.
     *
     * @return true if recording started successfully
     */
    fun startRecording(): Boolean {
        Log.d(TAG, "startRecording() called")
        if (_state.value is VoiceRecordingState.Recording) {
            Log.d(TAG, "startRecording() - already recording")
            return false
        }

        if (!hasMicrophonePermission()) {
            Log.e(TAG, "startRecording() - no microphone permission")
            _state.value = VoiceRecordingState.Error.permissionDenied()
            return false
        }

        if (!hasApiKey()) {
            Log.e(TAG, "startRecording() - no API key")
            _state.value = VoiceRecordingState.Error.apiKeyMissing()
            return false
        }

        // Initialize voice client with current API key
        val apiKey = prefs.voice.apiKey.get()
        Log.d(TAG, "startRecording() - API key length: ${apiKey.length}")
        voiceClient = OpenRouterVoiceClient(apiKey)

        if (!audioRecorder.start()) {
            Log.e(TAG, "startRecording() - failed to start recorder")
            _state.value = VoiceRecordingState.Error.recorderFailed()
            return false
        }

        Log.d(TAG, "startRecording() - recorder started successfully")
        hasVibratedWarning = false
        _state.value = VoiceRecordingState.Recording(
            elapsedSeconds = 0,
            promptMode = currentPromptMode
        )

        // Start countdown timer
        startTimer()
        
        // Vibrate to indicate recording started
        vibrate(50)

        return true
    }

    /**
     * Stops recording and triggers transcription.
     */
    fun stopAndTranscribe() {
        Log.d(TAG, "stopAndTranscribe() called, current state: ${_state.value}")
        if (_state.value !is VoiceRecordingState.Recording) {
            Log.d(TAG, "stopAndTranscribe() - not recording, returning")
            return
        }

        stopTimer()
        val audioFile = audioRecorder.stop()
        Log.d(TAG, "stopAndTranscribe() - audioFile: $audioFile, exists: ${audioFile?.exists()}, size: ${audioFile?.length()}")

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "stopAndTranscribe() - audio file invalid")
            _state.value = VoiceRecordingState.Error.recorderFailed()
            return
        }

        _state.value = VoiceRecordingState.Processing(currentPromptMode)
        Log.d(TAG, "stopAndTranscribe() - launching transcription")
        
        scope.launch {
            transcribeAndInsert(audioFile)
        }
    }

    /**
     * Cancels the current recording without transcribing.
     */
    fun cancelRecording() {
        stopTimer()
        audioRecorder.cancel()
        _state.value = VoiceRecordingState.Idle
        vibrate(30)
    }

    /**
     * Dismisses the current error state and returns to idle.
     */
    fun dismissError() {
        if (_state.value is VoiceRecordingState.Error) {
            _state.value = VoiceRecordingState.Idle
        }
    }

    /**
     * Cleans up resources. Call when the service is destroyed.
     */
    fun destroy() {
        stopTimer()
        audioRecorder.cancel()
        scope.cancel()
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(MAX_DURATION_MS, TICK_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsedMs = MAX_DURATION_MS - millisUntilFinished
                val elapsedSeconds = (elapsedMs / 1000).toInt()
                
                _state.value = VoiceRecordingState.Recording(
                    elapsedSeconds = elapsedSeconds,
                    promptMode = currentPromptMode
                )

                // Vibrate at 9-minute mark (1 minute warning)
                if (!hasVibratedWarning && elapsedMs >= WARNING_MS) {
                    hasVibratedWarning = true
                    vibrate(200)
                }
            }

            override fun onFinish() {
                // Auto-stop at 10 minutes
                vibrate(100)
                stopAndTranscribe()
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private suspend fun transcribeAndInsert(audioFile: File) {
        Log.d(TAG, "transcribeAndInsert() - starting with file: ${audioFile.absolutePath}, size: ${audioFile.length()}")
        val client = voiceClient ?: run {
            Log.e(TAG, "transcribeAndInsert() - no voice client!")
            _state.value = VoiceRecordingState.Error.apiKeyMissing()
            audioRecorder.deleteFile(audioFile)
            return
        }

        try {
            val audioBytes = withContext(Dispatchers.IO) {
                audioFile.readBytes()
            }
            Log.d(TAG, "transcribeAndInsert() - read ${audioBytes.size} bytes")

            // Delete file immediately after reading
            audioRecorder.deleteFile(audioFile)

            val customPrompt = if (currentPromptMode == PromptMode.CUSTOM) {
                prefs.voice.customPrompt.get().takeIf { it.isNotBlank() }
            } else null

            Log.d(TAG, "transcribeAndInsert() - calling API with mode: $currentPromptMode, format: ${audioRecorder.audioFormat}")
            val result = client.transcribe(
                audioBytes = audioBytes,
                audioFormat = audioRecorder.audioFormat,
                promptMode = currentPromptMode,
                customPrompt = customPrompt
            )

            result.fold(
                onSuccess = { text ->
                    Log.d(TAG, "transcribeAndInsert() - success! text: $text")
                    // Insert text at cursor position
                    insertText(text)
                    _state.value = VoiceRecordingState.Success(text)
                    // Return to idle after brief delay for UI feedback
                    kotlinx.coroutines.delay(500)
                    _state.value = VoiceRecordingState.Idle
                },
                onFailure = { exception ->
                    Log.e(TAG, "transcribeAndInsert() - failed: ${exception.message}", exception)
                    _state.value = when (exception) {
                        is OpenRouterException.Unauthorized -> VoiceRecordingState.Error.invalidApiKey()
                        is OpenRouterException.RateLimited -> {
                            // Auto-retry once after 3 seconds
                            retryAfterDelay(audioBytes, customPrompt)
                            return
                        }
                        is OpenRouterException.PayloadTooLarge -> VoiceRecordingState.Error.audioTooLong()
                        is OpenRouterException.Timeout,
                        is OpenRouterException.NetworkError -> VoiceRecordingState.Error.networkError()
                        is OpenRouterException.EmptyResponse -> VoiceRecordingState.Error.emptyResponse()
                        else -> VoiceRecordingState.Error(
                            message = exception.message ?: "Unknown error",
                            isRecoverable = true
                        )
                    }
                }
            )
        } catch (e: Exception) {
            audioRecorder.deleteFile(audioFile)
            _state.value = VoiceRecordingState.Error(
                message = e.message ?: "Unknown error",
                isRecoverable = true
            )
        }
    }

    private suspend fun retryAfterDelay(audioBytes: ByteArray, customPrompt: String?) {
        kotlinx.coroutines.delay(3000)
        
        val client = voiceClient ?: run {
            _state.value = VoiceRecordingState.Error.rateLimited()
            return
        }

        val result = client.transcribe(
            audioBytes = audioBytes,
            audioFormat = audioRecorder.audioFormat,
            promptMode = currentPromptMode,
            customPrompt = customPrompt
        )

        result.fold(
            onSuccess = { text ->
                insertText(text)
                _state.value = VoiceRecordingState.Success(text)
                kotlinx.coroutines.delay(500)
                _state.value = VoiceRecordingState.Idle
            },
            onFailure = {
                _state.value = VoiceRecordingState.Error.rateLimited()
            }
        )
    }

    private fun insertText(text: String) {
        Log.d(TAG, "insertText() - inserting: '$text', inputConnection: $inputConnection")
        inputConnection?.let { ic ->
            ic.beginBatchEdit()
            val result = ic.commitText(text, 1)
            ic.endBatchEdit()
            Log.d(TAG, "insertText() - commitText result: $result")
        } ?: Log.e(TAG, "insertText() - inputConnection is null!")
    }

    @Suppress("DEPRECATION")
    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }
}
