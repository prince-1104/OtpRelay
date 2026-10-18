package com.prince.otprelay.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.prince.otprelay.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || !hasSmsPermission(context)) return
        val parts = try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty() || messages.size > 32) {
                DetectionStatusStore.report(DetectionStatus.INVALID_BROADCAST)
                return
            }
            messages.map { message -> SmsPart(message?.originatingAddress, message?.messageBody) }
        } catch (_: RuntimeException) {
            // Malformed extras/PDUs and permission races must not crash or log payloads.
            DetectionStatusStore.report(DetectionStatus.INVALID_BROADCAST)
            return
        }
        if (MultipartSms.combine(parts) == null) {
            DetectionStatusStore.report(DetectionStatus.INVALID_BROADCAST)
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // No service, scheduled retry, database query, or retained SMS payload.
                // Bound work so every goAsync result is finished within the receiver window.
                withTimeout(8_000) {
                    val settings = SettingsRepository(appContext).settings.first()
                    if (IncomingSmsProcessor().detect(parts, settings, hasSmsPermission(appContext))) {
                        DetectionStatusStore.report(DetectionStatus.LIKELY_OTP_OBSERVED)
                    }
                }
            } catch (_: Exception) {
                // Settings I/O errors, cancellation/timeout and permission races fail closed.
                DetectionStatusStore.report(DetectionStatus.PROCESSING_UNAVAILABLE)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    private fun hasSmsPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}
