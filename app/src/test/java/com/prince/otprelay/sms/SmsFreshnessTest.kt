package com.prince.otprelay.sms

import org.junit.Assert.*
import org.junit.Test

class SmsFreshnessTest {
    private val receipt = 1_700_000_000_000L

    @Test fun enforcesExactAgeAndFutureSkewBoundaries() {
        assertTrue(SmsFreshness.isFresh(receipt, receipt))
        assertTrue(SmsFreshness.isFresh(receipt - SmsFreshness.MAX_AGE_MS, receipt))
        assertFalse(SmsFreshness.isFresh(receipt - SmsFreshness.MAX_AGE_MS - 1, receipt))
        assertTrue(SmsFreshness.isFresh(receipt + SmsFreshness.MAX_FUTURE_SKEW_MS, receipt))
        assertFalse(SmsFreshness.isFresh(receipt + SmsFreshness.MAX_FUTURE_SKEW_MS + 1, receipt))
    }

    @Test fun delayedProtectedSmsIsNotGivenANewCandidateLifetime() {
        assertFalse(SmsFreshness.isFresh(receipt - 3 * 60 * 60 * 1_000L, receipt))
    }

    @Test fun unknownNegativeAndImplausibleTimestampsFailClosed() {
        listOf(0L, -1L, Long.MAX_VALUE).forEach { assertFalse(SmsFreshness.isFresh(it, receipt)) }
        assertFalse(SmsFreshness.isFresh(receipt, 0))
    }
}
