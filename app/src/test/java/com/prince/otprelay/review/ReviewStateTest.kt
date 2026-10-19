package com.prince.otprelay.review

import com.prince.otprelay.sending.SendPhase
import com.prince.otprelay.sending.SendState
import org.junit.Assert.*
import org.junit.Test

class ReviewStateTest {
    private val candidate = OtpCandidate("token", "482619", "VM-TESTBK", "+442079460018", 120_000)

    @Test fun sendStatusCannotHydrateAnUnvalidatedCandidate() {
        val state = ReviewState(loading = true)
        assertNull(state.withSendState(SendState(), true, candidate).candidate)
    }

    @Test fun pauseExpiryAndReplacementClearAlreadyValidatedDetails() {
        val state = ReviewState(candidate)
        assertNull(state.withSendState(SendState(), false, candidate).candidate)
        assertNull(state.withSendState(SendState(), true, null).candidate)
        val replacement = OtpCandidate("other", "391742", "VM-TESTBK", "+442079460018", 120_000)
        assertNull(state.withSendState(SendState(), true, replacement).candidate)
    }

    @Test fun sentAndUnknownOutcomesRemoveCodeFromUiState() {
        listOf(SendPhase.SENT, SendPhase.UNKNOWN).forEach {
            assertNull(ReviewState(candidate).withSendState(SendState(it), true, candidate).candidate)
        }
    }

    @Test fun knownFailureCanKeepOnlyTheSameValidatedCandidateForExplicitRetry() {
        val state = ReviewState(candidate)
        val updated = state.withSendState(SendState(SendPhase.FAILED), true, candidate)
        assertSame(candidate, updated.candidate)
        assertTrue(updated.send.canAttempt)
        assertFalse(updated.busy)
    }
}
