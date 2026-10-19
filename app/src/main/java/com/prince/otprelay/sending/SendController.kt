package com.prince.otprelay.sending

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SendPhase { READY, PREPARING, SENDING, SENT, FAILED, UNKNOWN }

enum class SendFailure(val explanation: String) {
    PERMISSION("SMS sending permission is not granted. Enable SMS permission in Android Settings > Apps > OtpRelay > Permissions. Nothing was submitted."),
    INVALID_NUMBER("The saved recipient number is invalid. Correct it in settings. Nothing was submitted."),
    SETTINGS_CHANGED("The candidate expired or saved settings changed. Nothing was submitted."),
    NO_SIM("No usable SIM is present. Insert or enable a SIM before trying again."),
    DEFAULT_SIM("Choose a default SMS SIM in Android SIM settings. OtpRelay will not pick a SIM automatically."),
    SIM_NOT_READY("The configured SMS SIM is locked, disabled, or not ready. Check Android SIM settings."),
    UNSUPPORTED("This device does not support SMS sending."),
    RADIO_OFF("SMS send failed: the radio is off. Check airplane mode and SIM settings."),
    NO_SERVICE("SMS send failed: no mobile service is available."),
    LIMIT("Android blocked sending because an SMS limit was reached."),
    BLOCKED("Android or the SIM blocked sending this SMS."),
    CANCELLED("The review screen was left before submission. Nothing was submitted."),
    SETTINGS_UNAVAILABLE("Saved settings could not be checked. Nothing was submitted."),
    PREPARATION("SMS sending could not be prepared. Nothing was submitted."),
    GENERIC("The device reported that SMS sending failed. Delivery is not confirmed.")
}

data class SendState(val phase: SendPhase = SendPhase.READY,
    val attempt: String? = null, val failure: SendFailure? = null, val retryBlocked: Boolean = false) {
    val inProgress get() = phase == SendPhase.PREPARING || phase == SendPhase.SENDING
    val canAttempt get() = !retryBlocked && (phase == SendPhase.READY || phase == SendPhase.FAILED)
}

/** Payload-free state machine. It cannot send, retry, or hold an OTP or recipient. */
class SendController {
    private val mutableStates = MutableStateFlow<Map<String, SendState>>(emptyMap())
    val states = mutableStates.asStateFlow()

    @Synchronized fun state(token: String) = mutableStates.value[token] ?: SendState()

    /** Must only be called in response to the review screen's Send to Dad tap. */
    @Synchronized fun begin(token: String): String? {
        if (!state(token).canAttempt || mutableStates.value.values.any { it.inProgress }) return null
        val entries = mutableStates.value.toMutableMap()
        while (entries.size >= 64) entries.remove(entries.keys.first())
        val attempt = UUID.randomUUID().toString()
        entries[token] = SendState(SendPhase.PREPARING, attempt)
        mutableStates.value = entries
        return attempt
    }

    @Synchronized fun submitted(token: String, attempt: String): Boolean {
        val current = state(token)
        if (current.attempt != attempt || current.phase != SendPhase.PREPARING) return false
        set(token, current.copy(phase = SendPhase.SENDING))
        return true
    }

    @Synchronized fun preparationFailed(token: String, attempt: String, reason: SendFailure) {
        val current = state(token)
        if (current.attempt == attempt && current.phase == SendPhase.PREPARING)
            set(token, current.copy(phase = SendPhase.FAILED, failure = reason))
    }

    @Synchronized fun result(token: String, attempt: String, failure: SendFailure?): Boolean {
        val current = state(token)
        if (current.attempt != attempt || current.phase !in setOf(SendPhase.SENDING, SendPhase.UNKNOWN)) return false
        set(token, current.copy(phase = if (failure == null) SendPhase.SENT else SendPhase.FAILED, failure = failure))
        return true
    }

    @Synchronized fun timeout(token: String, attempt: String): Boolean {
        val current = state(token)
        if (current.attempt != attempt || current.phase != SendPhase.SENDING) return false
        set(token, current.copy(phase = SendPhase.UNKNOWN, retryBlocked = true))
        return true
    }

    private fun set(token: String, state: SendState) { mutableStates.value = mutableStates.value + (token to state) }
}
