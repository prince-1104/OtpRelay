package com.prince.otprelay.sms

import com.prince.otprelay.settings.RelaySettings
import org.junit.Assert.*
import org.junit.Test

class IncomingSmsProcessorTest {
    private val processor = IncomingSmsProcessor()
    private val settings = RelaySettings("+442079460018", setOf("VM-TESTBK"), true)

    @Test fun joinsMultipartWordsAndCodeWithoutAddedSeparators() {
        val parts = listOf(SmsPart("VM-TESTBK", "Your one-time pass"),
            SmsPart("VM-TESTBK", "word is 482"), SmsPart("VM-TESTBK", "619. Keep it private."))
        assertTrue(processor.detect(parts, settings, true))
        assertEquals("VM-TESTBK", MultipartSms.combine(parts)!!.sender)
    }

    @Test fun permissionAndSavedEnablementAreRequired() {
        val parts = listOf(SmsPart("VM-TESTBK", "OTP 482619"))
        assertFalse(processor.detect(parts, settings, false))
        assertFalse(processor.detect(parts, settings.copy(detectionEnabled = false), true))
        assertFalse(processor.detect(parts, settings.copy(approvedSenders = emptySet()), true))
    }

    @Test fun rejectsEmptyNullMixedSenderAndOversizedParts() {
        val malformed = listOf(
            emptyList(), listOf(SmsPart(null, "OTP 482619")),
            listOf(SmsPart("VM-TESTBK", null)),
            listOf(SmsPart("VM-TESTBK", "OTP "), SmsPart(null, "482619")),
            listOf(SmsPart("VM-TESTBK", "OTP "), SmsPart("VM-OTHER", "482619")),
            listOf(SmsPart("VM-TESTBK", " ")),
            listOf(SmsPart("VM-TESTBK", "OTP 482619" + " ".repeat(16_384))),
            List(33) { SmsPart("VM-TESTBK", "OTP 482619") }
        )
        malformed.forEach {
            assertNull(MultipartSms.combine(it))
            assertFalse(processor.detect(it, settings, true))
        }
    }

    @Test fun invalidSavedConfigurationCannotEnterDetection() {
        val parts = listOf(SmsPart("VM-TESTBK", "OTP 482619"))
        listOf(settings.copy(recipient = ""), settings.copy(recipient = "not a number"),
            settings.copy(approvedSenders = setOf("VM-TEſTBK"))).forEach {
            assertFalse(processor.eligible(it, true))
            assertNull(processor.candidate(parts, it, true))
        }
    }

    @Test fun preservesPartOrderEvenIfTimestampOrderDiffers() {
        val parts = listOf(SmsPart("VM-TESTBK", "OTP 482", 200), SmsPart("VM-TESTBK", "619", 100))
        val combined = MultipartSms.combine(parts)!!
        assertEquals("OTP 482619", combined.body)
        assertEquals(200L, combined.timestampMillis)
    }

    @Test fun multipartSenderNormalizationDoesNotAcceptLookalikes() {
        assertTrue(processor.detect(listOf(SmsPart("vm-testbk", "OTP "), SmsPart("VM-TESTBK", "482619")), settings, true))
        assertFalse(processor.detect(listOf(SmsPart("VM-TESTBK", "OTP "), SmsPart("VM-TEſTBK", "482619")), settings, true))
    }
}
