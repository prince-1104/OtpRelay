package com.prince.otprelay.review

import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prince.otprelay.settings.SettingsRepository
import com.prince.otprelay.settings.SenderIds
import com.prince.otprelay.settings.PhoneNumbers
import com.prince.otprelay.sending.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ReviewViewModel(application: Application, private val id: String) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val mutableState = MutableStateFlow(ReviewState(loading = true))
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var visible = false

    init {
        viewModelScope.launch {
            CandidateSessions.token.collect { current ->
                if (current != id) {
                    refreshJob?.cancel()
                    unavailable()
                }
            }
        }
        viewModelScope.launch {
            SendSessions.controller.states.collect { states ->
                val send = states[id] ?: SendState()
                mutableState.value = mutableState.value.withSendState(send, visible, CandidateSessions.find(id))
            }
        }
    }

    fun refresh() {
        visible = true
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val send = SendSessions.controller.state(id)
            mutableState.value = ReviewState(loading = true, busy = send.inProgress, send = send)
            try {
                val candidate = validated()
                if (candidate == null) unavailable()
                else {
                    val current = SendSessions.controller.state(id)
                    mutableState.value = ReviewState(candidate, busy = current.inProgress, send = current)
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                mutableState.value = ReviewState(message = "Could not check saved settings. Reopen review to try again.", send = SendSessions.controller.state(id))
            }
        }
    }

    fun hide() {
        visible = false
        refreshJob?.cancel()
        val send = SendSessions.controller.state(id)
        mutableState.value = ReviewState(busy = send.inProgress, send = send)
    }

    /** The sole entry point for sending, wired only to the Send to Dad button. */
    fun sendToDad(bank: String, amount: String, stillConfirmed: () -> Boolean) {
        if (!visible || mutableState.value.candidate == null || !stillConfirmed()) return
        if (SmsTextFormatter.format(bank, requireNotNull(mutableState.value.candidate).code, amount) == null) {
            mutableState.value = mutableState.value.copy(message = "Correct the optional bank/amount fields or clear them to send without those details.")
            return
        }
        val attempt = SendSessions.controller.begin(id) ?: run {
            mutableState.value = mutableState.value.copy(message = "A send is already in progress or this candidate has already been submitted. Wait for its result.")
            return
        }
        mutableState.value = mutableState.value.copy(busy = true, message = null, send = SendSessions.controller.state(id))
        viewModelScope.launch {
            try {
                val candidate = withTimeout(8_000) { validated() }
                when {
                    candidate == null -> SendSessions.controller.preparationFailed(id, attempt, SendFailure.SETTINGS_CHANGED)
                    !visible || !stillConfirmed() -> SendSessions.controller.preparationFailed(id, attempt, SendFailure.CANCELLED)
                    PhoneNumbers.normalize(candidate.recipient) != candidate.recipient ->
                        SendSessions.controller.preparationFailed(id, attempt, SendFailure.INVALID_NUMBER)
                    else -> ConfirmedSmsSender.submit(getApplication(), candidate, attempt, bank, amount)
                }
            } catch (exception: Exception) {
                SendSessions.controller.preparationFailed(id, attempt, SendFailure.SETTINGS_UNAVAILABLE)
                if (exception is CancellationException) throw exception
            }
        }
    }

    private suspend fun validated(): OtpCandidate? {
        val settings = repository.settings.first()
        val candidate = CandidateSessions.find(id) ?: return null
        if (!settings.detectionEnabled || settings.recipient != candidate.recipient ||
            settings.approvedSenders.none { SenderIds.normalize(it) == candidate.sender }) {
            CandidateSessions.discard(getApplication(), id)
            return null
        }
        return candidate
    }

    private fun unavailable() {
        val send = SendSessions.controller.state(id)
        mutableState.value = ReviewState(busy = send.inProgress, send = send,
            message = if (send.phase == SendPhase.READY || send.phase == SendPhase.FAILED)
                "This candidate expired, was replaced, or is no longer eligible." else null)
    }

    class Factory(private val app: Context, private val id: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReviewViewModel::class.java))
            return ReviewViewModel(app.applicationContext as Application, id) as T
        }
    }
}
