package com.prince.otprelay.history

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prince.otprelay.ui.theme.OtpRelayTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

object VerificationDates {
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    fun display(at: Long): String = format.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
    fun parse(raw: String): Long? = runCatching {
        LocalDateTime.parse(raw.trim(), format).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()?.takeIf { it > 0 }
}

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            OtpRelayTheme {
                val model: HistoryViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        HistoryViewModel(HistoryRepository(applicationContext)) as T
                })
                val state by model.state.collectAsStateWithLifecycle()
                HistoryScreen(state, model::add, model::verified, model::delete, model::clear, ::finish)
            }
        }
    }
}

@Composable
private fun HistoryScreen(state: HistoryState, onAdd: (String, Long, String?, String) -> Unit,
    onVerified: (String, Boolean) -> Unit, onDelete: (String) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    var bank by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(VerificationDates.display(System.currentTimeMillis())) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val enabled = !state.loading && !state.busy && state.error == null
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            if (it == "Record saved on this device.") { bank = ""; amount = "" }
            snackbar.showSnackbar(it)
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("Back to settings") }
            Text("Verification history", style = MaterialTheme.typography.headlineMedium)
            Text("Manual notes only, not proof of payment or SMS delivery. Never enter an OTP here. Up to 200 records stay on this device; oldest dates are removed when full.")
            OutlinedTextField(bank, { bank = it; error = null }, label = { Text("Bank name") },
                supportingText = { Text("Letters and spaces only; no codes, account numbers or messages.") },
                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it; error = null }, label = { Text("Date/time (yyyy-MM-dd HH:mm)") },
                supportingText = { Text("Your phone’s local time zone. Edit to match your bank record.") },
                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = it; error = null }, label = { Text("Amount (optional)") },
                supportingText = { Text("Positive amount, up to two decimal places. No commas.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VerificationInput.currencies.forEach { code ->
                    FilterChip(selected = currency == code, onClick = { currency = code },
                        enabled = enabled, label = { Text(code) })
                }
            }
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val parsedBank = VerificationInput.bank(bank)
                val parsedDate = VerificationDates.parse(date)
                val parsedAmount = if (amount.isBlank()) null else VerificationInput.amount(amount)
                error = when {
                    parsedBank == null -> "Enter a bank name (2–60 characters, without digits)."
                    parsedDate == null -> "Enter a valid date/time, for example 2026-10-19 19:30."
                    amount.isNotBlank() && parsedAmount == null -> "Enter a positive amount with at most two decimal places."
                    else -> null
                }
                if (error == null) onAdd(requireNotNull(parsedBank), requireNotNull(parsedDate), parsedAmount, currency)
            }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Saving…" else "Save manual record")
            }
            if (state.loading) CircularProgressIndicator()
            if (!state.loading && state.records.isEmpty()) Text("No records yet. Add a manual note above to verify later.")
            state.records.forEach { record ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.bank, style = MaterialTheme.typography.titleMedium)
                        Text(VerificationDates.display(record.occurredAt))
                        Text(record.amount?.let { "${record.currency} $it" } ?: "Amount not entered")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(record.verified, { onVerified(record.id, it) }, enabled = enabled)
                            Text("Manually verified", Modifier.weight(1f))
                            TextButton(onClick = { onDelete(record.id) }, enabled = enabled) { Text("Delete") }
                        }
                    }
                }
            }
            if (state.records.isNotEmpty()) OutlinedButton(onClick = { confirmClear = true }, enabled = enabled) { Text("Clear history") }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("Delete all records?") }, text = { Text("This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
