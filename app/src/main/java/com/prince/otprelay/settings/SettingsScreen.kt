package com.prince.otprelay.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRecipient: (String) -> Unit,
    onSenderDraft: (String) -> Unit,
    onAddSender: () -> Unit,
    onRemoveSender: (String) -> Unit,
    onDetection: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    permissionContent: @Composable () -> Unit
) {
    Scaffold { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().imePadding()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("OtpRelay", style = MaterialTheme.typography.headlineLarge)
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Text("Stored only on this device. No OTP codes or message history are stored.")
                if (state.loading) {
                    CircularProgressIndicator()
                    Text("Loading settings…")
                }
                state.message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                if (state.loadFailed) OutlinedButton(onClick = onRetry) { Text("Retry loading") }
                Text("Trusted recipient", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.recipient, onValueChange = onRecipient,
                    modifier = Modifier.fillMaxWidth(), enabled = state.editable,
                    label = { Text("Phone number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = state.errors.recipient != null,
                    supportingText = { Text(state.errors.recipient ?: "Include + and the country code, or use 00. Leave empty only when detection is off.") }
                )
                Text("Approved bank senders", style = MaterialTheme.typography.titleMedium)
                Text("Enter an exact sender ID or short code. Letters are saved in uppercase; hyphens are preserved. Approval does not verify a sender’s identity.")
                if (state.senders.isEmpty()) Text("No approved senders yet. Add one before enabling detection.")
                state.senders.sorted().forEach { sender ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(sender, Modifier.weight(1f))
                            TextButton(onClick = { onRemoveSender(sender) }, enabled = state.editable) { Text("Remove $sender") }
                        }
                    }
                }
                state.errors.senders?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = state.senderDraft, onValueChange = onSenderDraft,
                    modifier = Modifier.fillMaxWidth(), enabled = state.editable,
                    label = { Text("Sender ID or short code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    isError = state.senderError != null,
                    supportingText = { Text(state.senderError ?: "For example, VM-HDFCBK or 567890. Changes take effect after Save.") }
                )
                OutlinedButton(onClick = onAddSender, enabled = state.editable && state.senderDraft.isNotBlank()) { Text("Add sender") }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Enable OTP detection", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(checked = state.detectionEnabled, onCheckedChange = onDetection, enabled = state.editable,
                                modifier = Modifier.semantics { contentDescription = "Enable OTP detection" })
                        }
                        Text("Applies after Save. Detection checks eligible incoming SMS only when this saved setting is on and SMS permission is granted. Android may still withhold OTP messages.")
                        Text("Sending will always require an explicit user action.")
                    }
                }
                permissionContent()
                Button(onClick = onSave, enabled = state.editable, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.saving) "Saving…" else "Save settings")
                }
            }
        }
    }
}
