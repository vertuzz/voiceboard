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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.voiceInputManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.compose.stringRes

/**
 * Overlay displayed during voice recording and processing.
 * Shows recording timer and transcription mode buttons.
 */
@Composable
fun VoiceRecordingOverlay() {
    val context = LocalContext.current
    val voiceInputManager by context.voiceInputManager()
    val state by voiceInputManager.state.collectAsState()
    
    // Check if custom prompt is configured
    val prefs by FlorisPreferenceStore
    val customPrompt by prefs.voice.customPrompt.observeAsState()
    val hasCustomPrompt = customPrompt.isNotBlank()

    val showOverlay = state !is VoiceRecordingState.Idle

    // Use similar structure to BottomSheetHostUi for proper touch handling
    val bgColor = if (showOverlay) Color.Black.copy(alpha = 0.7f) else Color.Transparent
    
    if (showOverlay) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            // Top scrim area - catches touches and allows dismissing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // Tapping outside the modal - could dismiss, but we'll just consume
                        }
                    },
            )
            
            // Content area - centered modal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) {
                    when (val currentState = state) {
                        is VoiceRecordingState.Recording -> RecordingContent(
                            state = currentState,
                            hasCustomPrompt = hasCustomPrompt,
                            onStopWithMode = { mode -> voiceInputManager.stopAndTranscribe(mode) },
                            onCancel = { voiceInputManager.cancelRecording() }
                        )
                        is VoiceRecordingState.Processing -> ProcessingContent(currentState)
                        is VoiceRecordingState.Error -> ErrorContent(
                            state = currentState,
                            onDismiss = { voiceInputManager.dismissError() }
                        )
                        is VoiceRecordingState.Success -> { /* Brief flash, handled by manager */ }
                        is VoiceRecordingState.Idle -> { /* Not shown */ }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingContent(
    state: VoiceRecordingState.Recording,
    hasCustomPrompt: Boolean,
    onStopWithMode: (PromptMode) -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header with cancel button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringRes(R.string.voice__recording__title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pulsing mic icon with timer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(scale)
                    .background(
                        color = Color.Red.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = Color.Red,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Timer display
            Text(
                text = formatTime(state.elapsedSeconds),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isNearLimit) Color.Red else MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.isNearLimit) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.voice__recording__time_remaining, "time" to formatTime(state.remainingSeconds)),
                color = Color.Red,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mode selection buttons
        Text(
            text = "Tap to stop and transcribe:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Primary button - Clean (most common)
        Button(
            onClick = { onStopWithMode(PromptMode.CLEAN) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Clean Transcript",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Raw button
            ModeButton(
                text = "Raw",
                icon = Icons.Default.RecordVoiceOver,
                modifier = Modifier.weight(1f),
                onClick = { onStopWithMode(PromptMode.RAW) }
            )
            
            // Translate button
            ModeButton(
                text = "To English",
                icon = Icons.Default.Translate,
                modifier = Modifier.weight(1f),
                onClick = { onStopWithMode(PromptMode.TRANSLATE) }
            )
        }

        // Custom button (full width, less prominent) - only shown if custom prompt is configured
        if (hasCustomPrompt) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onStopWithMode(PromptMode.CUSTOM) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Custom Prompt",
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ProcessingContent(state: VoiceRecordingState.Processing) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 4.dp,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringRes(R.string.voice__processing__title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = state.promptMode.displayName,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    state: VoiceRecordingState.Error,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringRes(R.string.voice__error__title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = state.message,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onDismiss) {
            Text("Dismiss")
        }
    }
}

/**
 * Formats seconds into MM:SS format.
 */
private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
