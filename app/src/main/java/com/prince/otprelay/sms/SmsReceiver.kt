package com.prince.otprelay.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.prince.otprelay.settings.SettingsRepository
import com.prince.otprelay.review.CandidateSessions
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
        val pending = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // No service, scheduled retry, database query, or retained SMS payload.
                // Bound work so every goAsync result is finished within the receiver window.
                withTimeout(8_000) {
                    val settings = SettingsRepository(appContext).settings.first()
                    val processor = IncomingSmsProcessor()
                    // Do not decode SMS content at all while disabled or settings are invalid.
                    if (!processor.eligible(settings, hasSmsPermission(appContext))) return@withTimeout
                    val parts = try {
                        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                        if (messages.isNullOrEmpty() || messages.size > 32) {
                            DetectionStatusStore.report(DetectionStatus.INVALID_BROADCAST)
                            return@withTimeout
                        }
                        messages.map { message -> SmsPart(message?.originatingAddress, message?.messageBody, message?.timestampMillis ?: 0) }
                    } catch (_: RuntimeException) {
                        DetectionStatusStore.report(DetectionStatus.INVALID_BROADCAST)
                        return@withTimeout
                    }
                    if (MultipartSms.combine(parts) == null) {
                        DetectionStatusStore.report(DetectionStatus.INVALID_BROADCAST)
                        return@withTimeout
                    }
                    if (parts.any { !SmsFreshness.isFresh(it.timestampMillis, System.currentTimeMillis()) }) {
                        DetectionStatusStore.report(DetectionStatus.STALE_MESSAGE)
                        return@withTimeout
                    }
                    val candidate = processor.candidate(parts, settings, hasSmsPermission(appContext))
                    if (candidate != null) {
                        DetectionStatusStore.report(DetectionStatus.LIKELY_OTP_OBSERVED)
                        CandidateSessions.offer(appContext, candidate)
                    }
                }
            } catch (_: Exception) {
                // Settings I/O errors, cancellation/timeout and permission races fail closed.
                DetectionStatusStore.report(DetectionStatus.PROCESSING_UNAVAILABLE)
            } finally {
                try { pending.finish() } finally { scope.cancel() }
            }
        }
    }

    private fun hasSmsPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}
