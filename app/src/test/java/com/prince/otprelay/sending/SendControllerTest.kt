package com.prince.otprelay.sending

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class SendControllerTest {
    private val controller = SendController()

    @Test fun requestAcceptanceIsNotSentAndOnlyResultConfirmsSuccess() {
        assertEquals(SendPhase.READY, controller.state("candidate").phase)
        val attempt = controller.begin("candidate")!!
        assertEquals(SendPhase.PREPARING, controller.state("candidate").phase)
        assertFalse(controller.result("candidate", attempt, null))
        assertTrue(controller.submitted("candidate", attempt))
        assertEquals(SendPhase.SENDING, controller.state("candidate").phase)
        assertTrue(controller.state("candidate").inProgress)
        assertTrue(controller.result("candidate", attempt, null))
        assertEquals(SendPhase.SENT, controller.state("candidate").phase)
        assertNull(controller.begin("candidate"))
    }

    @Test fun rapidTapsAcrossReviewInstancesSubmitOnlyOnce() {
        var submissions = 0
        repeat(20) {
            val attempt = controller.begin("candidate")
            if (attempt != null && controller.submitted("candidate", attempt)) submissions++
        }
        assertEquals(1, submissions)
        assertNull(controller.begin("another-candidate"))
    }

    @Test fun concurrentTapsClaimExactlyOneAttempt() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val attempts = pool.invokeAll(List(16) { Callable { controller.begin("candidate") } })
            assertEquals(1, attempts.count { it.get() != null })
        } finally { pool.shutdownNow() }
    }

    @Test fun failureNeverRetriesAndExplicitTapCreatesNewAttempt() {
        val first = controller.begin("candidate")!!
        controller.submitted("candidate", first)
        controller.result("candidate", first, SendFailure.RADIO_OFF)
        assertEquals(SendPhase.FAILED, controller.state("candidate").phase)
        assertEquals(first, controller.state("candidate").attempt)
        assertEquals(SendFailure.RADIO_OFF, controller.state("candidate").failure)
        assertFalse(controller.state("candidate").inProgress)
        val retry = controller.begin("candidate")!! // Another deliberate button tap.
        assertNotEquals(first, retry)
        assertFalse(controller.result("candidate", first, null))
        assertEquals(retry, controller.state("candidate").attempt)
        controller.submitted("candidate", retry)
        controller.result("candidate", retry, null)
        assertEquals(SendPhase.SENT, controller.state("candidate").phase)
    }

    @Test fun permissionAndOtherPreparationFailuresDoNotEnterSending() {
        listOf(SendFailure.PERMISSION, SendFailure.NO_SIM, SendFailure.UNSUPPORTED,
            SendFailure.INVALID_NUMBER, SendFailure.DEFAULT_SIM, SendFailure.SIM_NOT_READY,
            SendFailure.SETTINGS_CHANGED, SendFailure.CANCELLED).forEach { reason ->
            val attempt = controller.begin("candidate")!!
            controller.preparationFailed("candidate", attempt, reason)
            assertEquals(SendPhase.FAILED, controller.state("candidate").phase)
            assertEquals(reason, controller.state("candidate").failure)
            assertFalse(controller.submitted("candidate", attempt))
        }
    }

    @Test fun missingResultIsUnknownNotSuccessOrRetryableFailure() {
        val attempt = controller.begin("candidate")!!
        controller.submitted("candidate", attempt)
        assertTrue(controller.timeout("candidate", attempt))
        assertEquals(SendPhase.UNKNOWN, controller.state("candidate").phase)
        assertFalse(controller.state("candidate").canAttempt)
        assertNull(controller.begin("candidate"))
        // A late real callback can still report the true outcome.
        assertTrue(controller.result("candidate", attempt, null))
        assertEquals(SendPhase.SENT, controller.state("candidate").phase)
    }

    @Test fun staleDuplicateAndUnknownCallbacksCannotChangeState() {
        assertFalse(controller.result("unknown", "attempt", null))
        val attempt = controller.begin("candidate")!!
        controller.submitted("candidate", attempt)
        assertFalse(controller.result("candidate", "other-attempt", null))
        controller.result("candidate", attempt, null)
        assertFalse(controller.result("candidate", attempt, SendFailure.GENERIC))
        assertFalse(controller.timeout("candidate", attempt))
        assertEquals(SendPhase.SENT, controller.state("candidate").phase)
    }
}
