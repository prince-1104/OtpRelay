package com.prince.otprelay.sms

import com.prince.otprelay.settings.SenderIds
import org.junit.Assert.*
import org.junit.Test

class SenderAllowlistTest {
    private val detector = OtpDetector()
    private val allowed = setOf("VM-TESTBK", "567890")

    @Test fun unicodeLookalikesCannotFoldIntoAnApprovedAsciiSender() {
        listOf("VM-TEſTBK", "VM-TESTBK", "VM-TЕSTBK", "VM-TESTBK\u200b", "５６７８９０").forEach {
            assertNull(SenderIds.normalize(it))
            assertFalse(detector.isLikelyOtp(it, "OTP 482619", allowed))
        }
    }

    @Test fun approvalIsAnExactIdNotSubstringOrWildcard() {
        assertTrue(detector.isLikelyOtp(" vm-testbk ", "OTP 482619", allowed))
        listOf("TESTBK", "AD-TESTBK", "VM-TESTBK-X", "VM-TESTBK*", "OTHER").forEach {
            assertFalse(detector.isLikelyOtp(it, "OTP 482619", allowed))
        }
    }

    @Test fun numericShortCodeApprovalPreservesLeadingZeros() {
        assertTrue(detector.isLikelyOtp("001234", "OTP 482619", setOf("001234")))
        assertFalse(detector.isLikelyOtp("1234", "OTP 482619", setOf("001234")))
        assertFalse(detector.isLikelyOtp("+567890", "OTP 482619", allowed))
        assertFalse(detector.isLikelyOtp(null, "OTP 482619", allowed))
    }
}
