package com.prince.otprelay.review

import com.prince.otprelay.sending.SendPhase
import com.prince.otprelay.sending.SendState

data class ReviewState(val candidate: OtpCandidate? = null, val loading: Boolean = false,
    val busy: Boolean = false, val message: String? = null, val send: SendState = SendState()) {
    /** Result events may clear a validated candidate, but must never hydrate one. */
    fun withSendState(next: SendState, visible: Boolean, active: OtpCandidate?): ReviewState = copy(
        send = next, busy = next.inProgress,
        message = if (next.phase != send.phase) null else message,
        candidate = candidate?.takeIf {
            visible && active === it && next.phase !in setOf(SendPhase.SENT, SendPhase.UNKNOWN)
        }
    )
}
