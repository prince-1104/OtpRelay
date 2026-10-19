package com.prince.otprelay.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryState(
    val records: List<VerificationRecord> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    private val mutable = MutableStateFlow(HistoryState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.records.collect { mutable.value = mutable.value.copy(records = it, loading = false) }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                mutable.value = mutable.value.copy(loading = false, error = "Could not load history. Reopen this screen to retry.")
            }
        }
    }

    fun add(bank: String, at: Long, amount: String?, currency: String) = write("Record saved on this device.") {
        repository.add(bank, at, amount, currency)
    }
    fun verified(id: String, value: Boolean) = write("Verification updated.") { repository.verified(id, value) }
    fun delete(id: String) = write("Record deleted.") { repository.delete(id) }
    fun clear() = write("History cleared.") { repository.clear() }

    private fun write(notice: String, action: suspend () -> Unit) {
        if (mutable.value.loading || mutable.value.busy || mutable.value.error != null) return
        mutable.value = mutable.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            try {
                action()
                mutable.value = mutable.value.copy(busy = false, notice = notice)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                mutable.value = mutable.value.copy(busy = false, notice = "Could not save this change. Try again.")
            }
        }
    }
}
