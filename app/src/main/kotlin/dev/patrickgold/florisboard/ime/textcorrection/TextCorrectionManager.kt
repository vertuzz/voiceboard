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

import android.content.Context
import android.util.Log
import android.view.inputmethod.InputConnection
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.voice.OpenRouterException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central manager for text correction functionality.
 *
 * Orchestrates text retrieval, correction via OpenRouter API, and text replacement.
 * Provides state flow for UI observation.
 */
class TextCorrectionManager(private val context: Context) {

    companion object {
        private const val TAG = "TextCorrectionManager"
        private const val MAX_TEXT_LENGTH = 10000 // Reasonable limit for text correction
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by FlorisPreferenceStore
    
    private var textCorrectionClient: TextCorrectionClient? = null
    
    private val _state = MutableStateFlow<TextCorrectionState>(TextCorrectionState.Idle)
    val state: StateFlow<TextCorrectionState> = _state.asStateFlow()

    private var inputConnection: InputConnection? = null

    /**
     * Checks if the OpenRouter API key is configured.
     */
    fun hasApiKey(): Boolean {
        return prefs.voice.apiKey.get().isNotBlank()
    }

    /**
     * Sets the input connection for text retrieval and replacement.
     */
    fun setInputConnection(ic: InputConnection?) {
        inputConnection = ic
    }

    /**
     * Retrieves all text from the current input field, sends it to the LLM for correction,
     * and replaces the original text with the corrected version.
     */
    suspend fun fixCurrentText() {
        Log.d(TAG, "fixCurrentText() called")
        
        if (_state.value is TextCorrectionState.Processing) {
            Log.d(TAG, "fixCurrentText() - already processing")
            return
        }

        val ic = inputConnection
        if (ic == null) {
            Log.e(TAG, "fixCurrentText() - no input connection")
            _state.value = TextCorrectionState.Error(
                message = "No input field available",
                isRecoverable = true
            )
            return
        }

        if (!hasApiKey()) {
            Log.e(TAG, "fixCurrentText() - no API key")
            _state.value = TextCorrectionState.Error.apiKeyMissing()
            return
        }

        // Initialize client with current API key
        val apiKey = prefs.voice.apiKey.get()
        textCorrectionClient = TextCorrectionClient(apiKey)

        // Retrieve all text from input field
        val allText = getAllTextFromField(ic)
        Log.d(TAG, "fixCurrentText() - retrieved text length: ${allText.length}")
        
        if (allText.isBlank()) {
            Log.e(TAG, "fixCurrentText() - no text to correct")
            _state.value = TextCorrectionState.Error.noTextToCorrect()
            return
        }

        if (allText.length > MAX_TEXT_LENGTH) {
            Log.e(TAG, "fixCurrentText() - text too long: ${allText.length}")
            _state.value = TextCorrectionState.Error(
                message = "Text too long to correct (max $MAX_TEXT_LENGTH characters)",
                isRecoverable = true
            )
            return
        }

        _state.value = TextCorrectionState.Processing
        Log.d(TAG, "fixCurrentText() - starting correction")
        
        correctAndReplace(allText, ic)
    }

    /**
     * Retrieves all text from the input field.
     * Uses getExtractedText for reliable retrieval of all text content.
     */
    private fun getAllTextFromField(ic: InputConnection): String {
        try {
            // Use getExtractedText to get all text from the field
            val request = android.view.inputmethod.ExtractedTextRequest()
            request.token = 0
            request.flags = 0
            
            val extractedText = ic.getExtractedText(request, 0)
            if (extractedText != null && extractedText.text != null) {
                return extractedText.text.toString()
            }
            
            // Fallback: Get text before and after cursor if getExtractedText fails
            val textBefore = ic.getTextBeforeCursor(MAX_TEXT_LENGTH, 0)?.toString() ?: ""
            val selectedText = ic.getSelectedText(0)?.toString() ?: ""
            val textAfter = ic.getTextAfterCursor(MAX_TEXT_LENGTH, 0)?.toString() ?: ""
            
            return if (selectedText.isNotEmpty()) {
                textBefore + selectedText + textAfter
            } else {
                textBefore + textAfter
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllTextFromField() - error: ${e.message}", e)
            return ""
        }
    }

    private suspend fun correctAndReplace(text: String, ic: InputConnection) {
        val client = textCorrectionClient ?: run {
            Log.e(TAG, "correctAndReplace() - no text correction client!")
            _state.value = TextCorrectionState.Error.apiKeyMissing()
            return
        }

        try {
            Log.d(TAG, "correctAndReplace() - calling API")
            val result = client.correctText(text)

            result.fold(
                onSuccess = { correctedText ->
                    Log.d(TAG, "correctAndReplace() - success! corrected text length: ${correctedText.length}")
                    
                    // Replace all text in the field
                    replaceAllText(ic, correctedText)
                    
                    _state.value = TextCorrectionState.Success
                    
                    // Return to idle after brief delay for UI feedback
                    kotlinx.coroutines.delay(1000)
                    _state.value = TextCorrectionState.Idle
                },
                onFailure = { exception ->
                    Log.e(TAG, "correctAndReplace() - failed: ${exception.message}", exception)
                    _state.value = when (exception) {
                        is OpenRouterException.Unauthorized -> TextCorrectionState.Error.invalidApiKey()
                        is OpenRouterException.RateLimited -> TextCorrectionState.Error.rateLimited()
                        is OpenRouterException.Timeout,
                        is OpenRouterException.NetworkError -> TextCorrectionState.Error.networkError()
                        is OpenRouterException.EmptyResponse -> TextCorrectionState.Error.emptyResponse()
                        else -> TextCorrectionState.Error(
                            message = exception.message ?: "Unknown error",
                            isRecoverable = true
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "correctAndReplace() - exception: ${e.message}", e)
            _state.value = TextCorrectionState.Error(
                message = e.message ?: "Unknown error",
                isRecoverable = true
            )
        }
    }

    /**
     * Replaces all text in the input field with the corrected text.
     * Uses setSelection for reliable text selection across all apps.
     */
    private fun replaceAllText(ic: InputConnection, newText: String) {
        try {
            ic.beginBatchEdit()
            
            // Get the current text length to determine selection range
            val request = android.view.inputmethod.ExtractedTextRequest()
            request.token = 0
            request.flags = 0
            val extractedText = ic.getExtractedText(request, 0)
            
            if (extractedText != null && extractedText.text != null) {
                val textLength = extractedText.text.length
                // Select all text by setting selection from 0 to text length
                ic.setSelection(0, textLength)
            } else {
                // Fallback: try performContextMenuAction if getExtractedText fails
                ic.performContextMenuAction(android.R.id.selectAll)
            }
            
            // Replace selected text with corrected text
            ic.commitText(newText, 1)
            
            ic.endBatchEdit()
            
            Log.d(TAG, "replaceAllText() - successfully replaced text")
        } catch (e: Exception) {
            Log.e(TAG, "replaceAllText() - error: ${e.message}", e)
        }
    }

    /**
     * Dismisses the current error state and returns to idle.
     */
    fun dismissError() {
        if (_state.value is TextCorrectionState.Error) {
            _state.value = TextCorrectionState.Idle
        }
    }

    /**
     * Cleans up resources. Call when the service is destroyed.
     */
    fun destroy() {
        scope.cancel()
    }
}
