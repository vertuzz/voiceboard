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

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client for transcribing audio via OpenRouter API using Gemini Flash 3 Preview.
 *
 * This client sends audio directly to OpenRouter's chat completions API,
 * using the multimodal capabilities of Gemini to transcribe speech.
 */
class OpenRouterVoiceClient(private val apiKey: String) {

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private const val MODEL = "google/gemini-3-flash-preview"
        private const val HTTP_REFERER = "com.voiceflow.keyboard"
        private const val X_TITLE = "VoiceFlow Keyboard"
        private const val TEMPERATURE = 0.2
        private const val MAX_TOKENS = 4096
        private const val TIMEOUT_SECONDS = 60L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Transcribes audio bytes using the specified prompt mode.
     *
     * @param audioBytes The raw audio bytes (MP3 or WAV format)
     * @param audioFormat The format of the audio ("mp3" or "wav")
     * @param promptMode The transcription mode to use
     * @param customPrompt Optional custom prompt (used when promptMode is CUSTOM)
     * @return Result containing the transcribed text or an error
     */
    suspend fun transcribe(
        audioBytes: ByteArray,
        audioFormat: String = "mp3",
        promptMode: PromptMode,
        customPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            val systemPrompt = promptMode.getEffectivePrompt(customPrompt)
            val userInstruction = promptMode.userInstruction

            val requestBody = buildRequestBody(base64Audio, audioFormat, systemPrompt, userInstruction)
            val request = buildRequest(requestBody)

            val response = httpClient.newCall(request).await()
            parseResponse(response)
        } catch (e: IOException) {
            Result.failure(OpenRouterException.NetworkError(e.message ?: "Network error"))
        } catch (e: Exception) {
            Result.failure(OpenRouterException.Unknown(e.message ?: "Unknown error"))
        }
    }

    private fun buildRequestBody(
        base64Audio: String, 
        audioFormat: String, 
        systemPrompt: String,
        userInstruction: String
    ): String {
        val requestJson = buildJsonObject {
            put("model", MODEL)
            put("messages", buildJsonArray {
                // System message with transcription instructions
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // User message with audio data
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", userInstruction)
                        })
                        add(buildJsonObject {
                            put("type", "input_audio")
                            put("input_audio", buildJsonObject {
                                put("data", base64Audio)
                                put("format", audioFormat)
                            })
                        })
                    })
                })
            })
            put("temperature", TEMPERATURE)
            put("max_tokens", MAX_TOKENS)
        }
        return requestJson.toString()
    }

    private fun buildRequest(requestBody: String): Request {
        return Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", HTTP_REFERER)
            .addHeader("X-Title", X_TITLE)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseResponse(response: Response): Result<String> {
        val responseBody = response.body?.string()
            ?: return Result.failure(OpenRouterException.EmptyResponse())

        return when (response.code) {
            200 -> {
                try {
                    val responseJson = json.decodeFromString<OpenRouterResponse>(responseBody)
                    val content = responseJson.choices.firstOrNull()?.message?.content
                    if (content.isNullOrBlank()) {
                        Result.failure(OpenRouterException.EmptyResponse())
                    } else {
                        Result.success(content.trim())
                    }
                } catch (e: Exception) {
                    Result.failure(OpenRouterException.ParseError(e.message ?: "Failed to parse response"))
                }
            }
            401 -> Result.failure(OpenRouterException.Unauthorized())
            429 -> Result.failure(OpenRouterException.RateLimited())
            413 -> Result.failure(OpenRouterException.PayloadTooLarge())
            408, 504 -> Result.failure(OpenRouterException.Timeout())
            in 500..599 -> Result.failure(OpenRouterException.ServerError(response.code))
            else -> Result.failure(OpenRouterException.HttpError(response.code, responseBody))
        }
    }

    /**
     * Extension function to await OkHttp call as a suspending function.
     */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }

        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }
}

/**
 * OpenRouter API response structure.
 */
@Serializable
data class OpenRouterResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val message: Message? = null,
    val index: Int = 0
)

@Serializable
data class Message(
    val role: String? = null,
    val content: String? = null
)

/**
 * Exceptions that can occur during OpenRouter API calls.
 */
sealed class OpenRouterException(message: String) : Exception(message) {
    class Unauthorized : OpenRouterException("Invalid OpenRouter API key")
    class RateLimited : OpenRouterException("Rate limit exceeded")
    class PayloadTooLarge : OpenRouterException("Audio payload too large")
    class Timeout : OpenRouterException("Request timed out")
    class EmptyResponse : OpenRouterException("Empty transcription response")
    class ParseError(details: String) : OpenRouterException("Failed to parse response: $details")
    class NetworkError(details: String) : OpenRouterException("Network error: $details")
    class ServerError(code: Int) : OpenRouterException("Server error: $code")
    class HttpError(code: Int, body: String) : OpenRouterException("HTTP $code: $body")
    class Unknown(details: String) : OpenRouterException("Unknown error: $details")
}
