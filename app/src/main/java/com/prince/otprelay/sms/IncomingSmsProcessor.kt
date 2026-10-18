package com.prince.otprelay.sms

import com.prince.otprelay.settings.RelaySettings
import com.prince.otprelay.settings.SenderIds

// Regular classes intentionally avoid generated toString methods exposing payloads.
class SmsPart(val sender: String?, val body: String?)
class IncomingSms(val sender: String, val body: String)

object MultipartSms {
    // The platform supplies the reassembled PDU sequence in order. Never sort by
    // timestamp or insert separators: a word or code may span two SMS parts.
    fun combine(parts: List<SmsPart>): IncomingSms? {
        if (parts.isEmpty() || parts.size > 32) return null
        val sender = parts.first().sender?.let(SenderIds::normalize) ?: return null
        if (parts.any { it.body == null || it.sender?.let(SenderIds::normalize) != sender }) return null
        if (parts.sumOf { it.body!!.length } > 16_384) return null
        val body = parts.joinToString("") { it.body!! }
        return if (body.isBlank()) null else IncomingSms(sender, body)
    }
}

class IncomingSmsProcessor(private val detector: OtpDetector = OtpDetector()) {
    fun detect(parts: List<SmsPart>, settings: RelaySettings, hasPermission: Boolean): Boolean {
        if (!hasPermission || !settings.detectionEnabled) return false
        val sms = MultipartSms.combine(parts) ?: return false
        return detector.isLikelyOtp(sms.sender, sms.body, settings.approvedSenders)
    }
}
