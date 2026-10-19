package com.prince.otprelay.review

import org.junit.Assert.*
import org.junit.Test

class ReviewPresentationGateTest {
    @Test fun waitsForUnlockWithoutConsumingTheCandidate() {
        val gate = ReviewPresentationGate()
        assertFalse(gate.take("candidate-a", true, false, true))
        assertTrue(gate.take("candidate-a", true, true, true))
    }

    @Test fun neverPresentsFromBackgroundEvenAfterUnlock() {
        val gate = ReviewPresentationGate()
        assertFalse(gate.take("candidate-a", false, true, true))
        assertTrue(gate.take("candidate-a", true, true, true))
    }

    @Test fun rejectsExpiredOrMissingCandidates() {
        val gate = ReviewPresentationGate()
        assertFalse(gate.take(null, true, true, true))
        assertFalse(gate.take("expired", true, true, false))
    }

    @Test fun repeatedUnlockAndResumeDoNotOpenDuplicateDialogs() {
        val gate = ReviewPresentationGate()
        assertTrue(gate.take("candidate-a", true, true, true))
        assertFalse(gate.take("candidate-a", true, true, true))
        assertTrue(gate.take("candidate-b", true, true, true))
    }
}
