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
        systemPrompt = """You are a speech-to-text transcriber. Your ONLY job is to output the exact words spoken.

CRITICAL RULES:
- Output ONLY the transcribed text, nothing else
- NO quotes around the text
- NO comments like "Here is your transcript" or "The user said:"
- NO explanations or meta-commentary
- Preserve the EXACT language the user speaks in (Ukrainian stays Ukrainian, German stays German, etc.)
- Preserve filler words and hesitations exactly as spoken
- If the audio is silent or inaudible, output nothing (empty response)

Bad example: "Привіт, як справи?"
Good example: Привіт, як справи?""",
        displayName = "Raw"
    ),

    @SerialName("clean")
    CLEAN(
        systemPrompt = """You are a speech-to-text transcriber. Your ONLY job is to output clean, polished text.

CRITICAL RULES:
- Output ONLY the transcribed text, nothing else
- NO quotes around the text
- NO comments like "Here is your transcript" or "The user said:"
- NO explanations or meta-commentary
- Preserve the EXACT language the user speaks in (Ukrainian stays Ukrainian, German stays German, etc.)
- Remove filler words (um, uh, еее, ммм)
- Fix grammar and punctuation
- Preserve the core meaning
- If the audio is silent or inaudible, output nothing (empty response)

Bad example: "Привіт, як справи?"
Good example: Привіт, як справи?""",
        displayName = "Clean"
    ),

    @SerialName("translate")
    TRANSLATE(
        systemPrompt = """You are a speech-to-text translator. Your ONLY job is to translate spoken words to English.

CRITICAL RULES:
- Output ONLY the English translation, nothing else
- NO quotes around the text
- NO comments like "Here is your translation" or "The user said:"
- NO explanations or meta-commentary
- Translate ANY source language to natural, fluent English
- If the audio is silent or inaudible, output nothing (empty response)

Bad example: "Hello, how are you?"
Good example: Hello, how are you?""",
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
