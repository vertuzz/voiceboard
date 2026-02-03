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

package dev.patrickgold.florisboard.ime.textcorrection

/**
 * Represents the current state of text correction.
 */
sealed class TextCorrectionState {
    /**
     * No correction in progress. Ready to start.
     */
    data object Idle : TextCorrectionState()

    /**
     * Processing text correction via LLM.
     */
    data object Processing : TextCorrectionState()

    /**
     * Text correction completed successfully.
     */
    data object Success : TextCorrectionState()

    /**
     * An error occurred during text correction.
     */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true
    ) : TextCorrectionState() {
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
            
            fun emptyResponse() = Error(
                message = "Text correction failed. Please try again.",
                isRecoverable = true
            )
            
            fun apiKeyMissing() = Error(
                message = "Please set your OpenRouter API key in Settings > Voice.",
                isRecoverable = true
            )
            
            fun noTextToCorrect() = Error(
                message = "No text to correct.",
                isRecoverable = true
            )
        }
    }
}
