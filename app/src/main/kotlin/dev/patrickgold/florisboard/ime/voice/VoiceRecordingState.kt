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

/**
 * Represents the current state of voice recording and transcription.
 */
sealed class VoiceRecordingState {
    /**
     * No recording in progress. Ready to start.
     */
    data object Idle : VoiceRecordingState()

    /**
     * Currently recording audio.
     * @param elapsedSeconds Number of seconds elapsed since recording started (0-600)
     * @param promptMode The transcription mode that will be used
     */
    data class Recording(
        val elapsedSeconds: Int = 0,
        val promptMode: PromptMode = PromptMode.CLEAN
    ) : VoiceRecordingState() {
        val remainingSeconds: Int get() = MAX_RECORDING_DURATION_SECONDS - elapsedSeconds
        val isNearLimit: Boolean get() = remainingSeconds <= WARNING_THRESHOLD_SECONDS
        
        companion object {
            const val MAX_RECORDING_DURATION_SECONDS = 600 // 10 minutes
            const val WARNING_THRESHOLD_SECONDS = 60 // 1 minute warning
        }
    }

    /**
     * Recording complete, now processing/transcribing.
     */
    data class Processing(
        val promptMode: PromptMode
    ) : VoiceRecordingState()

    /**
     * An error occurred during recording or transcription.
     */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true
    ) : VoiceRecordingState() {
        companion object {
            fun invalidApiKey() = Error(
                message = "Invalid OpenRouter API key. Check Settings > Voice > API Key.",
                isRecoverable = true
            )
            
            fun rateLimited() = Error(
                message = "Too many requests. Please wait a moment.",
                isRecoverable = true
            )
            
            fun networkError() = Error(
                message = "Connection failed. Check internet and try again.",
                isRecoverable = true
            )
            
            fun audioTooLong() = Error(
                message = "Audio too long. Maximum is 10 minutes.",
                isRecoverable = true
            )
            
            fun emptyResponse() = Error(
                message = "Transcription failed. Please speak clearly and try again.",
                isRecoverable = true
            )
            
            fun permissionDenied() = Error(
                message = "Microphone permission required for voice input.",
                isRecoverable = true
            )
            
            fun recorderFailed() = Error(
                message = "Failed to start audio recording.",
                isRecoverable = true
            )
            
            fun apiKeyMissing() = Error(
                message = "Please set your OpenRouter API key in Settings > Voice.",
                isRecoverable = true
            )
        }
    }

    /**
     * Transcription completed successfully.
     */
    data class Success(
        val transcribedText: String
    ) : VoiceRecordingState()
}
