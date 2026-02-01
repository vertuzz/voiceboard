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
enum class PromptMode(val systemPrompt: String, val displayName: String, val userInstruction: String) {
    @SerialName("raw")
    RAW(
        systemPrompt = """You are a speech-to-text transcriber. Your ONLY job is to output the exact words spoken.

CRITICAL RULES:
- Output ONLY the transcribed text, nothing else
- NO quotes around the text
- NO comments like "Here is your transcript" or "The user said:"
- NO explanations or meta-commentary
- Respond in the SAME language that the user speaks - auto-detect it from the audio
- Preserve filler words and hesitations exactly as spoken
- If the audio is silent or inaudible, output nothing (empty response)""",
        displayName = "Raw",
        userInstruction = "Transcribe the audio exactly as spoken."
    ),

    @SerialName("clean")
    CLEAN(
        systemPrompt = """You are a speech-to-text transcriber. Your ONLY job is to output clean, polished text.

CRITICAL RULES:
- Output ONLY the transcribed text, nothing else
- NO quotes around the text
- NO comments like "Here is your transcript" or "The user said:"
- NO explanations or meta-commentary
- Respond in the SAME language that the user speaks - auto-detect it from the audio
- Remove filler words (um, uh, like, you know, etc.)
- Fix grammar and punctuation
- Preserve the core meaning
- If the audio is silent or inaudible, output nothing (empty response)""",
        displayName = "Clean",
        userInstruction = "Transcribe the audio."
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
- If the audio is silent or inaudible, output nothing (empty response)""",
        displayName = "To English",
        userInstruction = "Translate the audio to English."
    ),

    @SerialName("custom")
    CUSTOM(
        systemPrompt = "",
        displayName = "Custom",
        userInstruction = "Process the audio according to the system instructions."
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
