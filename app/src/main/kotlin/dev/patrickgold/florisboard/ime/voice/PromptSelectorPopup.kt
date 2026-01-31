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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes

/**
 * Popup menu for selecting the transcription prompt mode.
 * Displayed on long-press of the voice input button.
 */
@Composable
fun PromptSelectorPopup(
    currentMode: PromptMode,
    onModeSelected: (PromptMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = stringRes(R.string.voice__prompt_selector__title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )

                PromptModeItem(
                    mode = PromptMode.RAW,
                    icon = Icons.Default.RecordVoiceOver,
                    isSelected = currentMode == PromptMode.RAW,
                    onClick = {
                        onModeSelected(PromptMode.RAW)
                        onDismiss()
                    }
                )

                PromptModeItem(
                    mode = PromptMode.CLEAN,
                    icon = Icons.Default.FormatClear,
                    isSelected = currentMode == PromptMode.CLEAN,
                    onClick = {
                        onModeSelected(PromptMode.CLEAN)
                        onDismiss()
                    }
                )

                PromptModeItem(
                    mode = PromptMode.TRANSLATE,
                    icon = Icons.Default.Translate,
                    isSelected = currentMode == PromptMode.TRANSLATE,
                    onClick = {
                        onModeSelected(PromptMode.TRANSLATE)
                        onDismiss()
                    }
                )

                PromptModeItem(
                    mode = PromptMode.CUSTOM,
                    icon = Icons.Default.Edit,
                    isSelected = currentMode == PromptMode.CUSTOM,
                    onClick = {
                        onModeSelected(PromptMode.CUSTOM)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun PromptModeItem(
    mode: PromptMode,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.displayName,
                fontSize = 16.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = getModeDescription(mode),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun getModeDescription(mode: PromptMode): String {
    return when (mode) {
        PromptMode.RAW -> stringRes(R.string.enum__prompt_mode__raw)
        PromptMode.CLEAN -> stringRes(R.string.enum__prompt_mode__clean)
        PromptMode.TRANSLATE -> stringRes(R.string.enum__prompt_mode__translate)
        PromptMode.CUSTOM -> stringRes(R.string.enum__prompt_mode__custom)
    }
}
