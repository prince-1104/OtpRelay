package com.prince.otprelay.sending

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

/** Receives results only. There is intentionally no sending or retry code in this receiver. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val uri = intent.data ?: return
        if (uri.scheme != "otprelay" || uri.authority != "sent" || uri.pathSegments.size != 2) return
        val failure = SmsSendResults.failure(resultCode)
        SendSessions.result(context, uri.pathSegments[0], uri.pathSegments[1], failure)
    }
    companion object { const val ACTION = "com.prince.otprelay.SMS_SENT_RESULT" }
}

object SmsSendResults {
    fun failure(resultCode: Int): SendFailure? = when (resultCode) {
            Activity.RESULT_OK -> null
            SmsManager.RESULT_ERROR_RADIO_OFF -> SendFailure.RADIO_OFF
            SmsManager.RESULT_ERROR_NO_SERVICE -> SendFailure.NO_SERVICE
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> SendFailure.LIMIT
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE,
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> SendFailure.BLOCKED
            else -> SendFailure.GENERIC
    }
}
