package com.prince.otprelay.settings

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object PhoneNumbers {
    private val util = PhoneNumberUtil.getInstance()

    // No default region, extensions, vanity letters, or silently discarded content.
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (!Regex("[+0-9(). \\-]+").matches(trimmed)) return null
        val compact = trimmed.replace(Regex("[(). \\-]"), "")
        val international = if (compact.startsWith("00")) "+${compact.drop(2)}" else compact
        if (!Regex("\\+[1-9][0-9]{6,14}").matches(international)) return null
        return try {
            val parsed = util.parse(international, "ZZ")
            if (util.isValidNumber(parsed)) util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            else null
        } catch (_: NumberParseException) {
            null
        }
    }
}

object SenderIds {
    fun normalize(input: String): String? {
        val value = input.trim().uppercase(Locale.ROOT)
        return value.takeIf {
            if (it.all(Char::isDigit)) Regex("[0-9]{3,8}").matches(it)
            else Regex("[A-Z0-9][A-Z0-9-]{1,19}").matches(it)
        }
    }
}

data class SettingsErrors(val recipient: String? = null, val senders: String? = null) {
    val isValid get() = recipient == null && senders == null
}

fun validateSettings(recipient: String, senders: Set<String>, enabled: Boolean): SettingsErrors =
    SettingsErrors(
        recipient = when {
            recipient.isBlank() && !enabled -> null
            PhoneNumbers.normalize(recipient) == null -> "Enter a valid phone number with a country code, such as +91 or +44."
            else -> null
        },
        senders = when {
            enabled && senders.isEmpty() -> "Add at least one approved sender before enabling detection."
            senders.any { SenderIds.normalize(it) == null } -> "Check the approved sender IDs."
            else -> null
        }
    )
