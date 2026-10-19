package com.prince.otprelay.review

import com.prince.otprelay.sms.DetectedOtp
import com.prince.otprelay.sms.TransactionDetailsDetector
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Deliberately no generated toString, Parcelable, Serializable, or persistence API.
class OtpCandidate(val token: String, val code: String, val sender: String,
    val recipient: String, val expiresAt: Long, val bank: String? = null, val amount: String? = null)

/** Bounded process-memory storage with an injected monotonic clock for tests. */
class CandidateStore(private val now: () -> Long) {
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val seen = linkedMapOf<String, Long>()
    private var active: OtpCandidate? = null

    @Synchronized fun offer(detection: DetectedOtp): String? {
        prune()
        val fingerprint = fingerprint(detection)
        if (seen.containsKey(fingerprint)) return null
        while (seen.size >= MAX_SEEN) seen.remove(seen.keys.first())
        val time = now()
        seen[fingerprint] = time + DEDUP_MS
        val token = UUID.randomUUID().toString()
        val details = TransactionDetailsDetector().detect(detection.sms.sender, detection.sms.body, detection.code)
        active = OtpCandidate(token, detection.code, detection.sms.sender, detection.recipient,
            time + LIFETIME_MS, details.bank, details.amount)
        return token
    }

    @Synchronized fun find(token: String): OtpCandidate? {
        prune()
        return active?.takeIf { it.token == token }
    }

    @Synchronized fun discard(token: String) {
        if (active?.token == token) active = null
        // Keep the fingerprint after cancellation/expiry so a replay does not notify again.
        prune()
    }

    private fun prune() {
        val time = now()
        if ((active?.expiresAt ?: Long.MAX_VALUE) <= time) active = null
        seen.entries.removeAll { it.value <= time }
    }

    private fun fingerprint(detection: DetectedOtp): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val sms = detection.sms
        // Only the keyed digest is retained; never retain the complete body.
        val bytes = mac.doFinal("${sms.sender}\u0000${sms.timestampMillis}\u0000${sms.body}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val LIFETIME_MS = 120_000L
        const val DEDUP_MS = 600_000L
        const val MAX_SEEN = 64
    }
}
