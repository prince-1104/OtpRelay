package com.prince.otprelay.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.prince.otprelay.sending.SendPhase

@Composable
fun ReviewScreen(state: ReviewState, onSend: () -> Unit, onCancel: () -> Unit,
    sendPermissionGranted: Boolean, permissionContent: @Composable () -> Unit) {
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
                Text("Expires after two minutes. Sends only the code with ‘OtpRelay OTP:’. Carrier charges may apply.")
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
        if (state.candidate != null) Button(onClick = onSend,
            enabled = !state.busy && state.send.canAttempt && sendPermissionGranted,
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Sending…" else "Send to Dad") }
        OutlinedButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.candidate == null) "Close" else "Cancel")
        }
       }
      }
    }
}
