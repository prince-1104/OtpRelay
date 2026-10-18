package com.prince.otprelay.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class SettingsUiState(
    val recipient: String = "",
    val senders: Set<String> = emptySet(),
    val senderDraft: String = "",
    val detectionEnabled: Boolean = false,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val errors: SettingsErrors = SettingsErrors(),
    val senderError: String? = null,
    val message: String? = null
) {
    val editable get() = !loading && !loadFailed && !saving
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()

    init { load() }

    fun load() {
        if (mutableState.value.saving) return
        mutableState.update { it.copy(loading = true, loadFailed = false, message = null) }
        viewModelScope.launch {
            try {
                val settings = repository.settings.first()
                mutableState.value = SettingsUiState(
                    recipient = settings.recipient, senders = settings.approvedSenders,
                    detectionEnabled = settings.detectionEnabled, loading = false
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                mutableState.update { it.copy(loading = false, loadFailed = true,
                    message = "Could not load settings. Retry before making changes.") }
            }
        }
    }

    fun recipientChanged(value: String) = edit { copy(recipient = value, errors = errors.copy(recipient = null)) }
    fun senderDraftChanged(value: String) = edit { copy(senderDraft = value, senderError = null) }
    fun detectionChanged(value: Boolean) = edit { copy(detectionEnabled = value, errors = SettingsErrors()) }
    fun removeSender(value: String) = edit { copy(senders = senders - value, errors = errors.copy(senders = null)) }

    fun addSender() = edit {
        val normalized = SenderIds.normalize(senderDraft)
        when {
            normalized == null -> copy(senderError = "Use 2–20 letters, digits or hyphens, or a 3–8 digit short code.")
            normalized in senders -> copy(senderError = "This sender is already approved.")
            else -> copy(senders = senders + normalized, senderDraft = "", senderError = null,
                errors = errors.copy(senders = null))
        }
    }

    fun save() {
        val state = mutableState.value
        if (!state.editable) return
        val errors = validateSettings(state.recipient, state.senders, state.detectionEnabled)
        if (!errors.isValid || state.senderDraft.isNotBlank()) {
            mutableState.update { it.copy(errors = errors, message = "Check the highlighted fields before saving.",
                senderError = if (state.senderDraft.isNotBlank()) "Add this sender or clear the field before saving." else it.senderError) }
            return
        }
        val settings = RelaySettings(
            if (state.recipient.isBlank()) "" else requireNotNull(PhoneNumbers.normalize(state.recipient)),
            state.senders, state.detectionEnabled
        )
        mutableState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                repository.save(settings)
                mutableState.update { it.copy(saving = false, recipient = settings.recipient,
                    errors = SettingsErrors(), message = "Settings saved on this device.") }
            } catch (_: IOException) {
                mutableState.update { it.copy(saving = false, message = "Could not save settings. Your edits are still here. Try again.") }
            }
        }
    }

    private fun edit(transform: SettingsUiState.() -> SettingsUiState) {
        mutableState.update { if (it.editable) transform(it).copy(message = null) else it }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(repository) as T
        }
    }
}
