package com.prince.otprelay.sending

/** Formats only the content confirmed in review. Never persist or log the result. */
object SmsTextFormatter {
    fun bank(raw: String): String? = raw.trim().takeIf { value ->
        value.length in 2..40 && value.any { it in 'A'..'Z' || it in 'a'..'z' } &&
            value.all { it in 'A'..'Z' || it in 'a'..'z' || it in " .&'-" }
    }

    fun validAmount(raw: String): Boolean = raw.isBlank() || amount(raw) != null

    private fun amount(raw: String): String? {
        val value = raw.trim()
        if (!Regex("[0-9]{1,12}(\\.[0-9]{1,2})?").matches(value)) return null
        return value.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            ?.stripTrailingZeros()?.toPlainString()
    }

    fun format(bank: String, code: String, amount: String): String? {
        val name = if (bank.isBlank()) "Bank unknown" else bank(bank) ?: return null
        if (!Regex("[0-9]{6}").matches(code) || !validAmount(amount)) return null
        val displayedAmount = if (amount.isBlank()) "not provided" else amount(amount) ?: return null
        return "$name : $code amount : $displayedAmount"
    }
}
