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

package dev.patrickgold.florisboard.app.settings.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.voice.PromptMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.voiceInputManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun VoiceScreen() = FlorisScreen {
    title = stringRes(R.string.settings__voice__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val voiceInputManager by context.voiceInputManager()

    content {
        val scope = rememberCoroutineScope()
        val apiKey by prefs.voice.apiKey.observeAsState()
        val defaultPromptMode by prefs.voice.defaultPromptMode.observeAsState()
        val customPrompt by prefs.voice.customPrompt.observeAsState()

        // API Key Section
        PreferenceGroup(title = stringRes(R.string.pref__voice__group_api__label)) {
            ApiKeyPreference(
                apiKey = apiKey,
                onApiKeyChange = { scope.launch { prefs.voice.apiKey.set(it) } }
            )
        }

        // Transcription Mode Section
        PreferenceGroup(title = stringRes(R.string.pref__voice__group_transcription__label)) {
            ListPreference(
                prefs.voice.defaultPromptMode,
                title = stringRes(R.string.pref__voice__default_prompt_mode__label),
                entries = enumDisplayEntriesOf(PromptMode::class),
            )
            
            // Custom prompt editor (only visible when CUSTOM is selected)
            if (defaultPromptMode == PromptMode.CUSTOM) {
                CustomPromptPreference(
                    customPrompt = customPrompt,
                    onCustomPromptChange = { scope.launch { prefs.voice.customPrompt.set(it) } }
                )
            }
        }

        // Permissions Section
        PreferenceGroup(title = stringRes(R.string.pref__voice__group_permissions__label)) {
            MicrophonePermissionPreference()
        }

        // Info Section
        PreferenceGroup(title = stringRes(R.string.pref__voice__group_info__label)) {
            Preference(
                title = stringRes(R.string.pref__voice__about__label),
                summary = stringRes(R.string.pref__voice__about__summary),
                onClick = { /* Could open OpenRouter website */ }
            )
        }
    }
}

@Composable
private fun ApiKeyPreference(
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    var showKeyInput by remember { mutableStateOf(false) }
    var tempKey by remember(apiKey) { mutableStateOf(apiKey) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringRes(R.string.pref__voice__api_key__label),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        if (apiKey.isBlank()) {
            Text(
                text = stringRes(R.string.pref__voice__api_key__not_set),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            // Mask the API key, showing only last 4 characters
            val maskedKey = "sk-or-v1-****...${apiKey.takeLast(4)}"
            Text(
                text = maskedKey,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showKeyInput) {
            OutlinedTextField(
                value = tempKey,
                onValueChange = { tempKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(R.string.pref__voice__api_key__hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onApiKeyChange(tempKey.trim())
                    showKeyInput = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(R.string.pref__voice__api_key__save))
            }
        } else {
            Button(
                onClick = { showKeyInput = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (apiKey.isBlank()) {
                        stringRes(R.string.pref__voice__api_key__set)
                    } else {
                        stringRes(R.string.pref__voice__api_key__change)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringRes(R.string.pref__voice__api_key__summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomPromptPreference(
    customPrompt: String,
    onCustomPromptChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringRes(R.string.pref__voice__custom_prompt__label),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = customPrompt,
            onValueChange = onCustomPromptChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringRes(R.string.pref__voice__custom_prompt__hint)) },
            minLines = 3,
            maxLines = 6,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringRes(R.string.pref__voice__custom_prompt__summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MicrophonePermissionPreference() {
    val context = LocalContext.current
    val voiceInputManager by context.voiceInputManager()
    var hasPermission by remember { mutableStateOf(voiceInputManager.hasMicrophonePermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringRes(R.string.pref__voice__microphone_permission__label),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        if (hasPermission) {
            Text(
                text = stringRes(R.string.pref__voice__microphone_permission__granted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = stringRes(R.string.pref__voice__microphone_permission__not_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(R.string.pref__voice__microphone_permission__request))
            }
        }
    }
}
