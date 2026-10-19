package com.prince.otprelay.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.prince.otprelay.sending.SendPhase
import com.prince.otprelay.sending.SmsTextFormatter

@Composable
fun ReviewScreen(state: ReviewState, onSend: (String, String) -> Unit, onCancel: () -> Unit,
    sendPermissionGranted: Boolean, permissionContent: @Composable () -> Unit) {
    // Not saveable: review metadata and preview disappear when the candidate is hidden or expires.
    var bank by remember(state.candidate?.token) { mutableStateOf("") }
    var amount by remember(state.candidate?.token) { mutableStateOf("") }
    val preview = state.candidate?.let { SmsTextFormatter.format(bank, it.code, amount) }
    Dialog(onDismissRequest = { if (!state.busy) onCancel() }, properties = DialogProperties(
        securePolicy = SecureFlagPolicy.SecureOn, dismissOnClickOutside = false)) {
      Surface(shape = MaterialTheme.shapes.extraLarge) {
       Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(24.dp),
           verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Send this OTP?", style = MaterialTheme.typography.headlineSmall)
            Text("Check the sender and recipient. A sender ID does not prove who sent the message.")
            if (state.loading) CircularProgressIndicator()
            state.candidate?.let { candidate ->
                Text("Detected code", style = MaterialTheme.typography.titleMedium)
                Text(candidate.code, style = MaterialTheme.typography.headlineLarge)
                Text("Sender: ${candidate.sender}")
                Text("Configured recipient: ${candidate.recipient}")
                OutlinedTextField(bank, { bank = it }, label = { Text("Bank name") },
                    placeholder = { Text("ICICI") }, singleLine = true,
                    enabled = !state.busy && state.send.canAttempt, modifier = Modifier.fillMaxWidth(),
                    isError = bank.isNotBlank() && SmsTextFormatter.bank(bank) == null,
                    supportingText = { Text("Enter the bank name (2–40 letters/spaces). Not inferred from the sender.") })
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (optional)") },
                    singleLine = true, enabled = !state.busy && state.send.canAttempt,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), isError = !SmsTextFormatter.validAmount(amount),
                    supportingText = { Text("Positive amount, up to two decimal places, without commas. Leave blank if unknown.") })
                if (preview != null) {
                    Text("SMS preview", style = MaterialTheme.typography.titleMedium)
                    Text(preview)
                }
                Text("Expires after two minutes. Only this preview is sent. Carrier charges may apply. These fields are not saved to history.")
            }
            Text(when (state.send.phase) {
                SendPhase.READY -> if (state.candidate != null) "Ready for your confirmation. No send is started automatically."
                    else "No candidate or send status is available in this app process. An earlier send may have been submitted; delivery is unknown."
                SendPhase.PREPARING -> "Checking saved settings and SMS availability…"
                SendPhase.SENDING -> "Sending — waiting for the device’s SMS send result. Leaving this screen will not cancel the submitted SMS."
                SendPhase.SENT -> "Sent — confirmed by the device’s SMS send callback. Delivery to the recipient is not confirmed."
                SendPhase.FAILED -> (state.send.failure?.explanation ?: "SMS send failed.") +
                    if (state.send.retryBlocked) " This attempt previously had an unknown result; another send remains blocked."
                    else " No automatic retry. If the candidate is still available, tap Send to Dad again only if you choose to retry."
                SendPhase.UNKNOWN -> "Send result unknown. The SMS may have been sent. Delivery is not confirmed. Another send is blocked to avoid a duplicate."
            })
            state.message?.let { Text(it) }
            permissionContent()
        }
        if (state.candidate != null) Button(onClick = { onSend(bank, amount) },
            enabled = !state.busy && state.send.canAttempt && sendPermissionGranted && preview != null,
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Sending…" else "Send to Dad") }
        OutlinedButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.candidate == null) "Close" else "Cancel")
        }
       }
      }
    }
}
