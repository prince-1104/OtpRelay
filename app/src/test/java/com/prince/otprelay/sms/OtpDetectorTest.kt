package com.prince.otprelay.sms

import org.junit.Assert.*
import org.junit.Test

/** All bodies and sender IDs below are invented test fixtures. */
class OtpDetectorTest {
    private val detector = OtpDetector()
    private val approved = setOf("VM-TESTBK", "567890")

    @Test fun recognizesKeywordVariationsAndBothCodePositions() {
        listOf(
            "Your OTP is 482619. Do not share it.",
            "OTP: 482619",
            "Your one-time password is 482619 for sign-in.",
            "Your ONE TIME PASSWORD: 482619",
            "Your verification code is 482619.",
            "Verification-code: 482619",
            "482619 is your OTP for login.",
            "Use 482619 as your verification code.",
            "OTP for your login is 482619"
        ).forEach { assertTrue("Expected a synthetic OTP classification", detector.isLikelyOtp("vm-testbk", it, approved)) }
        assertTrue(detector.isLikelyOtp("567890", "OTP 000123", approved))
    }

    @Test fun rejectsOrdinaryMessagesAndUnrelatedSixDigitNumbers() {
        listOf(
            "Your statement is ready.",
            "Your order number is 482619.",
            "A deposit of 482619 was received.",
            "Your postal code is 482619.",
            "OTP safety reminder. Your reference number is 482619.",
            "Your verification code will arrive soon. Invoice 482619 is due.",
            "Do not share any OTP. Call 482619 for support.",
            "OTP for transaction 482619 will arrive later.",
            "OTP for payment 482619 has not been sent.",
            "Photopaper order 482619 is ready.",
            "Your password is 482619.",
            "482619 OTP requests were blocked."
        ).forEach { assertFalse("Unrelated message must not match", detector.isLikelyOtp("VM-TESTBK", it, approved)) }
    }

    @Test fun requiresExactlySixStandaloneAsciiDigits() {
        listOf("OTP 12345", "OTP 1234567", "OTP A482619", "OTP 482619B",
            "OTP 482619.50", "OTP 482619,50", "OTP ４８２６１９", "OTP 482 619", "OTP coming soon")
            .forEach { assertFalse(detector.isLikelyOtp("VM-TESTBK", it, approved)) }
    }

    @Test fun rejectsUnapprovedMissingAndPartialSenders() {
        listOf(null, "", "OTHER", "TESTBK", "VM-TESTBK-EXTRA").forEach {
            assertFalse(detector.isLikelyOtp(it, "OTP is 482619", approved))
        }
        assertFalse(detector.isLikelyOtp("VM-TESTBK", "OTP is 482619", emptySet()))
        assertFalse(detector.isLikelyOtp("VM-TESTBK", null, approved))
        assertFalse(detector.isLikelyOtp("VM-TESTBK", "", approved))
    }

    @Test fun extractsOnlyUnambiguousContextualCandidate() {
        assertEquals("482619", detector.findCode("VM-TESTBK", "OTP: 482619", approved))
        assertNull(detector.findCode("VM-TESTBK", "OTP 482619. Another OTP 391742.", approved))
        assertNull(detector.findCode("OTHER", "OTP: 482619", approved))
    }

    @Test fun rejectsSixDigitFragmentsOfFormattedLongerNumbers() {
        listOf("OTP 482619-7890", "OTP 482619 / 7890", "OTP 482619:50",
            "OTP 482619 - 7890", "OTP 482619 7890", "1-482619 is your OTP", "12/482619 is your OTP")
            .forEach { assertNull("Formatted number must not become a candidate", detector.findCode("VM-TESTBK", it, approved)) }
    }

    @Test fun repeatedIdenticalCodeIsNotAnAmbiguousSecondCandidate() {
        assertEquals("482619", detector.findCode("VM-TESTBK", "OTP 482619. 482619 is your verification code.", approved))
    }

    @Test fun supportsLineBreaksAndPunctuationWithoutLooseningNumberBoundaries() {
        assertEquals("482619", detector.findCode("VM-TESTBK", "Your verification code:\n482619. Valid briefly.", approved))
        assertEquals("482619", detector.findCode("VM-TESTBK", "OTP-482619 expires shortly.", approved))
    }
}
