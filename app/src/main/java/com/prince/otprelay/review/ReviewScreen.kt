package com.prince.otprelay.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReviewScreen(state: ReviewState, onSend: () -> Unit, onCancel: () -> Unit) {
    Scaffold { insets ->
        Column(Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Review possible bank OTP", style = MaterialTheme.typography.headlineMedium)
            Text("Check the sender and recipient. A sender ID does not prove who sent the message.")
            if (state.loading) CircularProgressIndicator()
            state.candidate?.let { candidate ->
                Text("Detected code", style = MaterialTheme.typography.titleMedium)
                Text(candidate.code, style = MaterialTheme.typography.headlineLarge)
                Text("Sender: ${candidate.sender}")
                Text("Configured recipient: ${candidate.recipient}")
                Text("Expires two minutes after detection. This screen does not retain an OTP history.")
                Text("Send to Dad opens a draft in your default SMS app. Check it and tap Send there to send the code. No message has been sent yet.")
                Button(onClick = onSend, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Send to Dad")
                }
            }
            state.message?.let { Text(it) }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
