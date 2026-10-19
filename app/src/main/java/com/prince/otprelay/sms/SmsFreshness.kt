package com.prince.otprelay.sms

/** Wall-clock age only at receipt; candidate lifetime subsequently uses elapsed time.
 * Carrier timestamps can be unreliable, so uncertain/old messages fail closed.
 */
object SmsFreshness {
    const val MAX_AGE_MS = 120_000L
    const val MAX_FUTURE_SKEW_MS = 30_000L

    fun isFresh(timestamp: Long, receivedAt: Long): Boolean =
        timestamp > 0 && receivedAt > 0 &&
            timestamp >= receivedAt - MAX_AGE_MS &&
            timestamp <= receivedAt + MAX_FUTURE_SKEW_MS
}
