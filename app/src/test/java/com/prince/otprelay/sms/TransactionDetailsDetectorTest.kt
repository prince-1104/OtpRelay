package com.prince.otprelay.sms

import com.prince.otprelay.review.CandidateStore
import org.junit.Assert.*
import org.junit.Test

class TransactionDetailsDetectorTest {
    private val detector = TransactionDetailsDetector()

    @Test fun recognizesKnownSenderTokensAndExplicitBodyNames() {
        assertEquals("ICICI", detector.detect("AD-ICICIO-T", "Synthetic OTP 482619", "482619").bank)
        assertEquals("SBI", detector.detect("JX-SBIOTP-S", "Synthetic OTP 482619", "482619").bank)
        assertEquals("HDFC", detector.detect("VM-TESTBK", "HDFC Bank synthetic OTP 482619", "482619").bank)
        assertNull(detector.detect("VM-NOTICICI", "Synthetic OTP 482619", "482619").bank)
    }

    @Test fun ambiguousOrUnrecognizedBanksRemainOptional() {
        assertNull(detector.detect("AD-ICICIO-T", "Synthetic HDFC Bank OTP 482619", "482619").bank)
        assertNull(detector.detect("VM-TESTBK", "Synthetic OTP 482619", "482619").bank)
    }

    @Test fun extractsCurrencyMarkedTransactionAmountsWithGrouping() {
        listOf("for INR 899" to "899", "payment of Rs. 1,234.50" to "1234.5",
            "amount: ₹1,23,456.78" to "123456.78").forEach { (text, expected) ->
            assertEquals(expected, detector.detect("VM-TESTBK", "Synthetic OTP 482619 $text", "482619").amount)
        }
    }

    @Test fun unrelatedNumbersBalancesAndAmbiguousAmountsAreNotGuessed() {
        listOf("Synthetic OTP 482619 reference 899", "Synthetic OTP 482619. Balance Rs. 899",
            "Synthetic OTP 482619. Balance of INR 899", "Synthetic OTP 482619 for INR 10 or payment of INR 20",
            "Synthetic OTP 482619 for INR 1.234", "Synthetic OTP 482619 for INR 1,23",
            "Synthetic OTP 482619 for INR 482619", "Synthetic OTP 482619 for INR 0").forEach { body ->
            assertNull(body, detector.detect("VM-TESTBK", body, "482619").amount)
        }
    }

    @Test fun detailsRemainTransientWithCandidateExpiry() {
        var time = 1000L
        val store = CandidateStore { time }
        val token = requireNotNull(store.offer(DetectedOtp(
            IncomingSms("AD-ICICIO-T", "Synthetic OTP 482619 for INR 899"), "482619", "+442079460018")))
        val candidate = requireNotNull(store.find(token))
        assertEquals("ICICI", candidate.bank)
        assertEquals("899", candidate.amount)
        time += CandidateStore.LIFETIME_MS
        assertNull(store.find(token))
    }
}
