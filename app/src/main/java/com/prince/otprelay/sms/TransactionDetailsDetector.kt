package com.prince.otprelay.sms

/** Best-effort labels, never proof of a bank's identity or a completed payment. */
class TransactionDetails(val bank: String?, val amount: String?)

class TransactionDetailsDetector {
    fun detect(sender: String, body: String, code: String): TransactionDetails {
        val fromSender = sender.uppercase(java.util.Locale.ROOT).split('-').mapNotNull { senderBanks[it] }
        val fromBody = bankWords.filter { (_, pattern) -> pattern.containsMatchIn(body) }.keys
        val bank = (fromSender + fromBody).distinct().singleOrNull()
        val amounts = contextualAmount.findAll(body).map { match ->
            normalizeAmount(match.groupValues[1])
        }.toList()
        // Do not accidentally carry the detected authentication code into amount metadata.
        val amount = if (amounts.any { it == null }) null else
            amounts.filterNotNull().distinct().singleOrNull()?.takeUnless { it == code }
        return TransactionDetails(bank, amount)
    }

    private fun normalizeAmount(raw: String): String? {
        if (!Regex("(?:[0-9]{1,12}|[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]{1,2})?").matches(raw)) return null
        val plain = raw.replace(",", "")
        if (plain.substringBefore('.').length > 12) return null
        return plain.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.stripTrailingZeros()?.toPlainString()
    }

    private companion object {
        val senderBanks = mapOf("ICICIO" to "ICICI", "ICICIB" to "ICICI", "ICICI" to "ICICI",
            "SBIOTP" to "SBI", "SBICRD" to "SBI", "HDFCBK" to "HDFC",
            "AXISBK" to "AXIS", "RBLBNK" to "RBL")
        val bankWords = mapOf(
            "ICICI" to Regex("\\bICICI(?:\\s+Bank)?\\b", RegexOption.IGNORE_CASE),
            "SBI" to Regex("\\b(?:SBI|State Bank of India)\\b", RegexOption.IGNORE_CASE),
            "HDFC" to Regex("\\bHDFC(?:\\s+Bank)?\\b", RegexOption.IGNORE_CASE),
            "AXIS" to Regex("\\bAxis Bank\\b", RegexOption.IGNORE_CASE),
            "RBL" to Regex("\\bRBL(?:\\s+Bank)?\\b", RegexOption.IGNORE_CASE)
        )
        val contextualAmount = Regex(
            "\\b(?:for|amount(?:\\s+is)?|transaction(?:\\s+of)?|txn(?:\\s+of)?|payment(?:\\s+of)?|purchase(?:\\s+of)?|pay(?:ing)?)\\s*[:=]?\\s*(?:INR|Rs\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(?![A-Za-z0-9,]|\\.[0-9])",
            RegexOption.IGNORE_CASE
        )
    }
}
