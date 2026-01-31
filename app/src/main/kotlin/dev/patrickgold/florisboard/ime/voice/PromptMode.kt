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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the different transcription modes available for voice input.
 */
@Serializable
enum class PromptMode(val systemPrompt: String, val displayName: String) {
    @SerialName("raw")
    RAW(
        systemPrompt = "Transcribe the audio verbatim. Preserve filler words, hesitations, and original language exactly as spoken.",
        displayName = "Raw"
    ),

    @SerialName("clean")
    CLEAN(
        systemPrompt = "Transcribe and clean up: remove filler words (um, uh), fix grammar and punctuation, preserve core meaning.",
        displayName = "Clean"
    ),

    @SerialName("translate")
    TRANSLATE(
        systemPrompt = "Translate the speech to English regardless of source language. Output natural, fluent English translation only.",
        displayName = "Translate"
    ),

    @SerialName("custom")
    CUSTOM(
        systemPrompt = "",
        displayName = "Custom"
    );

    fun getEffectivePrompt(customPrompt: String?): String {
        return when (this) {
            CUSTOM -> customPrompt ?: CLEAN.systemPrompt
            else -> systemPrompt
        }
    }

    companion object {
        fun default() = CLEAN
    }
}
