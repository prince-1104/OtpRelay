package com.prince.otprelay.sending

import org.junit.Assert.*
import org.junit.Test

class SmsTextFormatterTest {
    @Test fun formatsConfirmedBankCodeAndAmount() {
        assertEquals("Example Bank : 482619 amount : 899", SmsTextFormatter.format(" Example Bank ", "482619", "899.00"))
        assertEquals("Example Bank : 482619 amount : 899.5", SmsTextFormatter.format("Example Bank", "482619", "899.50"))
    }

    @Test fun explicitlyLabelsAnUnknownAmount() {
        assertEquals("Example Bank : 482619 amount : not provided", SmsTextFormatter.format("Example Bank", "482619", ""))
    }

    @Test fun missingBankAndAmountNeverRequireTypingBeforeConfirmation() {
        assertEquals("Bank unknown : 482619 amount : not provided", SmsTextFormatter.format("", "482619", ""))
    }

    @Test fun rejectsInvalidFieldsAndMessageInjection() {
        listOf("Bank 482619", "Example\nBank", "Example|Bank", "A".repeat(41)).forEach {
            assertNull(SmsTextFormatter.format(it, "482619", "899"))
        }
        listOf("0", "-899", "899.001", "1,000", "899\nOTP", "1e3", "9".repeat(13)).forEach {
            assertNull(SmsTextFormatter.format("Example Bank", "482619", it))
        }
        listOf("12345", "1234567", "482619\n", "ABCDEF").forEach {
            assertNull(SmsTextFormatter.format("Example Bank", it, "899"))
        }
    }

    @Test fun longestAcceptedBodyRemainsSinglePartGsmText() {
        val body = requireNotNull(SmsTextFormatter.format("B".repeat(40), "482619", "999999999999.99"))
        assertTrue(body.length <= 160)
        assertTrue(body.all { it.code in 32..126 })
    }
}
