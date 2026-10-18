package com.prince.otprelay.sms

import com.prince.otprelay.settings.SenderIds

/** Conservative English-language heuristic, not proof of a bank's identity.
 * Only returns a classification; no code or SMS content escapes the detector.
 */
class OtpDetector {
    fun isLikelyOtp(sender: String?, body: String?, approvedSenders: Set<String>): Boolean {
        val normalizedSender = sender?.let(SenderIds::normalize) ?: return false
        if (approvedSenders.none { SenderIds.normalize(it) == normalizedSender }) return false
        if (body.isNullOrBlank()) return false
        return forward.containsMatchIn(body) || contextual.containsMatchIn(body) || reverse.containsMatchIn(body)
    }

    private companion object {
        const val KEYWORD = "\\b(?:otp|one[\\s-]+time[\\s-]+password|verification[\\s-]+code)\\b"
        const val CODE = "(?<![\\p{L}\\p{N}_.,])[0-9]{6}(?![\\p{L}\\p{N}_]|[.,][0-9])"
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
