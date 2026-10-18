package com.prince.otprelay.settings

import org.junit.Assert.*
import org.junit.Test

class PhoneNumbersTest {
    @Test fun normalizesInternationalFormatting() {
        assertEquals("+442079460018", PhoneNumbers.normalize(" +44 (20) 7946-0018 "))
        assertEquals("+442079460018", PhoneNumbers.normalize("0044 20 7946 0018"))
        assertEquals("+16502530000", PhoneNumbers.normalize("+1 (650) 253-0000"))
    }

    @Test fun rejectsAmbiguousInvalidAndUnsupportedInputs() {
        listOf("", "6502530000", "+999123456789", "+123", "+0123456789",
            "+1234567890123456", "+44 20 7946 0018 ext 2", "+1 CALL NOW",
            "++442079460018", "+44/2079460018", "+44２079460018").forEach {
            assertNull("Unexpectedly accepted input", PhoneNumbers.normalize(it))
        }
    }

    @Test fun detectionRequiresRecipientAndApprovedSender() {
        assertTrue(validateSettings("", emptySet(), false).isValid)
        assertFalse(validateSettings("", setOf("VM-HDFCBK"), true).isValid)
        assertFalse(validateSettings("+442079460018", emptySet(), true).isValid)
        assertFalse(validateSettings("bad", emptySet(), false).isValid)
        assertTrue(validateSettings("+442079460018", setOf("VM-HDFCBK"), true).isValid)
    }

    @Test fun normalizesAndValidatesSenderIds() {
        assertEquals("VM-HDFCBK", SenderIds.normalize(" vm-hdfcbk "))
        assertEquals("567890", SenderIds.normalize("567890"))
        listOf("", "12", "123456789", "bank name", "-BANK", "BANK!", "A".repeat(21)).forEach { assertNull(SenderIds.normalize(it)) }
    }
}
