package com.prince.otprelay.review

import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prince.otprelay.settings.SettingsRepository
import com.prince.otprelay.settings.SenderIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReviewState(val candidate: OtpCandidate? = null, val loading: Boolean = false,
    val busy: Boolean = false, val message: String? = null)

class ReviewViewModel(application: Application, private val id: String) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val mutableState = MutableStateFlow(ReviewState(loading = true))
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            CandidateSessions.token.collect { current ->
                if (current != id) {
                    refreshJob?.cancel()
                    unavailable()
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.value = ReviewState(loading = true)
            try {
                val candidate = validated()
                if (candidate == null) unavailable()
                else mutableState.value = ReviewState(candidate)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                mutableState.value = ReviewState(message = "Could not check saved settings. Reopen review to try again.")
            }
        }
    }

    fun hide() {
        refreshJob?.cancel()
        mutableState.value = ReviewState()
    }

    fun openDraft(onOpen: (OtpCandidate) -> String?) {
        if (mutableState.value.candidate == null || mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                // Recheck expiry AND current settings after the deliberate button tap.
                val candidate = validated()
                if (candidate == null) { unavailable(); return@launch }
                val error = onOpen(candidate)
                if (error == null) {
                    CandidateSessions.discard(getApplication(), id)
                    mutableState.value = ReviewState(message = "SMS draft opened. Sending still requires confirmation in your SMS app.")
                } else mutableState.value = ReviewState(CandidateSessions.find(id), message = error)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                mutableState.value = ReviewState(CandidateSessions.find(id), message = "Could not open an SMS draft. Nothing was sent.")
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
        mutableState.value = ReviewState(message = "This candidate expired, was replaced, or is no longer eligible. Nothing was sent.")
    }

    class Factory(private val app: Context, private val id: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReviewViewModel::class.java))
            return ReviewViewModel(app.applicationContext as Application, id) as T
        }
    }
}
