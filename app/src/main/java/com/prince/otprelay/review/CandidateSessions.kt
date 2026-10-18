package com.prince.otprelay.review

import android.content.Context
import android.os.SystemClock
import com.prince.otprelay.sms.DetectedOtp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CandidateSessions {
    private val store = CandidateStore(SystemClock::elapsedRealtime)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var expiry: Job? = null
    private val mutableToken = MutableStateFlow<String?>(null)
    val token = mutableToken.asStateFlow()

    @Synchronized fun offer(context: Context, detection: DetectedOtp) {
        val id = store.offer(detection) ?: return
        expiry?.cancel()
        mutableToken.value = id
        val app = context.applicationContext
        // Replaces the previous generic notification, never adds a history of codes.
        expiry = scope.launch {
            delay(CandidateStore.LIFETIME_MS)
            discard(app, id)
        }
        OtpNotifications.show(app, id)
    }

    fun find(id: String): OtpCandidate? = store.find(id)

    @Synchronized fun discard(context: Context, id: String) {
        store.discard(id)
        if (mutableToken.value == id) {
            mutableToken.value = null
            OtpNotifications.cancel(context)
        }
    }
}
