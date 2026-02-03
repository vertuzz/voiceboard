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

import dev.patrickgold.florisboard.ime.voice.Choice
import dev.patrickgold.florisboard.ime.voice.OpenRouterException
import dev.patrickgold.florisboard.ime.voice.OpenRouterResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
 * Client for correcting text via OpenRouter API using Gemini Flash 3 Preview.
 *
 * This client sends text to OpenRouter's chat completions API for grammar and style correction.
 */
class TextCorrectionClient(private val apiKey: String) {

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private const val MODEL = "google/gemini-3-flash-preview"
        private const val HTTP_REFERER = "com.voiceflow.keyboard"
        private const val X_TITLE = "VoiceFlow Keyboard"
        private const val TEMPERATURE = 0.2
        private const val MAX_TOKENS = 4096
        private const val TIMEOUT_SECONDS = 30L
        
        private const val SYSTEM_PROMPT = """You are a text editor. Your ONLY job is to fix grammatical and stylistic errors.

CRITICAL RULES:
- Output ONLY the corrected text, nothing else
- NO quotes around the text
- NO comments like "Here is the corrected version"
- Preserve the original meaning completely
- Fix spelling, grammar, punctuation, and style issues
- Maintain the same language as the input
- If the text is already correct, return it unchanged"""
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
     * Corrects the given text using the LLM.
     *
     * @param text The text to correct
     * @return Result containing the corrected text or an error
     */
    suspend fun correctText(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequestBody(text)
            val request = buildRequest(requestBody)

            val response = httpClient.newCall(request).await()
            parseResponse(response)
        } catch (e: IOException) {
            Result.failure(OpenRouterException.NetworkError(e.message ?: "Network error"))
        } catch (e: Exception) {
            Result.failure(OpenRouterException.Unknown(e.message ?: "Unknown error"))
        }
    }

    private fun buildRequestBody(text: String): String {
        val requestJson = buildJsonObject {
            put("model", MODEL)
            put("messages", buildJsonArray {
                // System message with correction instructions
                add(buildJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                // User message with text to correct
                add(buildJsonObject {
                    put("role", "user")
                    put("content", text)
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
