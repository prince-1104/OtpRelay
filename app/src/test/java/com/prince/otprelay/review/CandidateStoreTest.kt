package com.prince.otprelay.review

import com.prince.otprelay.sms.DetectedOtp
import com.prince.otprelay.sms.IncomingSms
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class CandidateStoreTest {
    private var time = 1_000L
    private val store = CandidateStore { time }
    private fun detection(timestamp: Long = 42, sender: String = "VM-TESTBK") =
        DetectedOtp(IncomingSms(sender, "Your synthetic OTP is 482619.", timestamp), "482619", "+442079460018")

    @Test fun candidateExpiresAtExactBoundary() {
        val id = store.offer(detection())!!
        assertEquals("482619", store.find(id)!!.code)
        assertEquals("VM-TESTBK", store.find(id)!!.sender)
        assertEquals("+442079460018", store.find(id)!!.recipient)
        time += CandidateStore.LIFETIME_MS - 1
        assertNotNull(store.find(id))
        time++
        assertNull(store.find(id))
    }

    @Test fun duplicateDoesNotCreateCandidateOrExtendExpiry() {
        val id = store.offer(detection())!!
        val expiry = store.find(id)!!.expiresAt
        time += 30_000
        assertNull(store.offer(detection()))
        assertEquals(expiry, store.find(id)!!.expiresAt)
        time = expiry
        assertNull(store.find(id))
        assertNull(store.offer(detection()))
    }

    @Test fun cancellationKeepsDedupFingerprint() {
        val id = store.offer(detection())!!
        store.discard(id)
        assertNull(store.find(id))
        assertNull(store.offer(detection()))
        time += CandidateStore.DEDUP_MS
        assertNotNull(store.offer(detection()))
    }

    @Test fun newMessageReplacesOldAndOldCancelCannotRemoveNew() {
        val first = store.offer(detection())!!
        val second = store.offer(detection(timestamp = 43))!!
        assertNotEquals(first, second)
        assertNull(store.find(first))
        store.discard(first)
        assertNotNull(store.find(second))
        assertNotNull(store.offer(detection(timestamp = 43, sender = "VM-OTHER")))
    }

    @Test fun oldOrUnknownTokenCannotAccessCandidateAfterProcessRecreation() {
        val id = store.offer(detection())!!
        assertNull(store.find("not-a-token"))
        assertNull(CandidateStore { time }.find(id))
        val candidate = store.find(id)!!
        assertTrue(candidate.toString().matches(Regex("com\\.prince\\.otprelay\\.review\\.OtpCandidate@[0-9a-f]+")))
    }

    @Test fun concurrentDuplicateOffersProduceOnlyOneNotificationToken() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(List(16) { Callable { store.offer(detection()) } })
            assertEquals(1, results.count { it.get() != null })
        } finally { pool.shutdownNow() }
    }
}
