package com.prince.otprelay.sms

import com.prince.otprelay.settings.SenderIds

/** Conservative English-language heuristic, not proof of a bank's identity.
 * Extracted codes are restricted to the transient review pipeline.
 */
class OtpDetector {
    fun isLikelyOtp(sender: String?, body: String?, approvedSenders: Set<String>): Boolean {
        return findCode(sender, body, approvedSenders) != null
    }

    /** The returned value must remain ephemeral and never enter logs or saved state. */
    fun findCode(sender: String?, body: String?, approvedSenders: Set<String>): String? {
        val normalizedSender = sender?.let(SenderIds::normalize) ?: return null
        if (approvedSenders.none { SenderIds.normalize(it) == normalizedSender }) return null
        if (body.isNullOrBlank()) return null
        val codes = sequenceOf(forward, contextual, reverse).flatMap { it.findAll(body) }
            .map { it.groups["code"]!!.value }.distinct().take(2).toList()
        return codes.singleOrNull() // Ambiguous messages must not offer the wrong code.
    }

    private companion object {
        const val KEYWORD = "\\b(?:otp|one[\\s-]+time[\\s-]+password|verification[\\s-]+code)\\b"
        const val CODE = "(?<![\\p{L}\\p{N}_.,])(?<code>[0-9]{6})(?![\\p{L}\\p{N}_]|[.,][0-9])"
        // Require a direct grammatical link, rather than any nearby number.
        val forward = Regex(
            "$KEYWORD(?:\\s+(?:code|number))?(?:\\s+is)?\\s*[:=\\-]?\\s*$CODE",
            RegexOption.IGNORE_CASE
        )
        val contextual = Regex(
            "$KEYWORD\\s+for\\s+(?:(?:your|the)\\s+)?(?:login|sign[ -]?in|transaction|payment|verification|access)\\s+is\\s*[:=\\-]?\\s*$CODE",
            RegexOption.IGNORE_CASE
        )
        val reverse = Regex(
            "$CODE\\s+(?:(?:is|as)\\s+)?(?:(?:your|the)\\s+)?$KEYWORD",
            RegexOption.IGNORE_CASE
        )
    }
}
