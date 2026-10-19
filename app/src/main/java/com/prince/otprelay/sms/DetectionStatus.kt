package com.prince.otprelay.sms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DetectionStatus {
    WAITING, LIKELY_OTP_OBSERVED, INVALID_BROADCAST, STALE_MESSAGE, PROCESSING_UNAVAILABLE
}

/** Process-local, payload-free status. No sender, body, code, or history is retained. */
object DetectionStatusStore {
    private val mutableStatus = MutableStateFlow(DetectionStatus.WAITING)
    val status = mutableStatus.asStateFlow()
    fun report(status: DetectionStatus) { mutableStatus.value = status }
}
