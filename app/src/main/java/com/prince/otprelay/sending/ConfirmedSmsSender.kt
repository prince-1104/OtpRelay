package com.prince.otprelay.sending

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.prince.otprelay.review.CandidateSessions
import com.prince.otprelay.review.OtpCandidate
import com.prince.otprelay.settings.PhoneNumbers

/** The only SmsManager call site. Invoked only by ReviewViewModel after a Send to Dad tap. */
internal object ConfirmedSmsSender {
    fun permissionGranted(context: Context) = ContextCompat.checkSelfPermission(context,
        Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun submit(context: Context, candidate: OtpCandidate, attempt: String, bank: String, amount: String) {
        val token = candidate.token
        fun reject(reason: SendFailure) = SendSessions.controller.preparationFailed(token, attempt, reason)
        val body = SmsTextFormatter.format(bank, candidate.code, amount)
            ?: run { reject(SendFailure.PREPARATION); return }
        if (!permissionGranted(context)) { reject(SendFailure.PERMISSION); return }
        if (PhoneNumbers.normalize(candidate.recipient) != candidate.recipient) { reject(SendFailure.INVALID_NUMBER); return }
        if (CandidateSessions.find(token) !== candidate) { reject(SendFailure.SETTINGS_CHANGED); return }
        var sent: PendingIntent? = null
        var submitted = false
        try {
            val feature = if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_MESSAGING else PackageManager.FEATURE_TELEPHONY
            if (!context.packageManager.hasSystemFeature(feature)) { reject(SendFailure.UNSUPPORTED); return }
            val telephony = context.getSystemService(TelephonyManager::class.java)
                ?: run { reject(SendFailure.UNSUPPORTED); return }
            val subscription = SubscriptionManager.getDefaultSmsSubscriptionId()
            val validSubscription = if (Build.VERSION.SDK_INT >= 29) SubscriptionManager.isValidSubscriptionId(subscription)
                else subscription >= 0 && subscription < Int.MAX_VALUE
            if (!validSubscription) {
                // Check logical slots without reading subscription identities or phone numbers.
                val slots = telephony.phoneCount.coerceIn(1, 8)
                val noSim = (0 until slots).all { telephony.getSimState(it) == TelephonyManager.SIM_STATE_ABSENT }
                reject(if (noSim) SendFailure.NO_SIM else SendFailure.DEFAULT_SIM)
                return
            }
            when (telephony.createForSubscriptionId(subscription).simState) {
                TelephonyManager.SIM_STATE_ABSENT -> { reject(SendFailure.NO_SIM); return }
                TelephonyManager.SIM_STATE_READY -> Unit
                else -> { reject(SendFailure.SIM_NOT_READY); return }
            }
            val manager = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subscription)
            else SmsManager.getSmsManagerForSubscriptionId(subscription)
            if (manager == null) { reject(SendFailure.UNSUPPORTED); return }
            if (SubscriptionManager.getDefaultSmsSubscriptionId() != subscription) { reject(SendFailure.DEFAULT_SIM); return }
            // No OTP, recipient, or body enters the result PendingIntent.
            val result = Intent(context, SmsSentReceiver::class.java).apply {
                action = SmsSentReceiver.ACTION
                data = Uri.Builder().scheme("otprelay").authority("sent").appendPath(token).appendPath(attempt).build()
            }
            sent = PendingIntent.getBroadcast(context, 0, result, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            if (!permissionGranted(context)) { sent.cancel(); reject(SendFailure.PERMISSION); return }
            if (CandidateSessions.find(token) !== candidate) { sent.cancel(); reject(SendFailure.SETTINGS_CHANGED); return }
            if (!SendSessions.controller.submitted(token, attempt)) { sent.cancel(); return }
            submitted = true
            // Minimal, single-part body. No full incoming SMS or unrelated message is forwarded.
            manager.sendTextMessage(candidate.recipient, null, body, sent, null)
            // Returning from sendTextMessage is NOT success. Only the sent callback confirms it.
            SendSessions.awaitResult(context, token, attempt)
        } catch (_: SecurityException) {
            sent?.cancel()
            if (submitted) SendSessions.result(context, token, attempt, SendFailure.PERMISSION) else reject(SendFailure.PERMISSION)
        } catch (_: IllegalArgumentException) {
            sent?.cancel()
            if (submitted) SendSessions.result(context, token, attempt, SendFailure.INVALID_NUMBER) else reject(SendFailure.INVALID_NUMBER)
        } catch (_: UnsupportedOperationException) {
            sent?.cancel()
            if (submitted) SendSessions.result(context, token, attempt, SendFailure.UNSUPPORTED) else reject(SendFailure.UNSUPPORTED)
        } catch (_: RuntimeException) {
            if (submitted) {
                // An unexpected platform exception after entry may have queued an SMS.
                // Never offer an unsafe automatic retry or claim a definitive failure.
                if (SendSessions.controller.timeout(token, attempt)) CandidateSessions.discard(context, token)
            } else { sent?.cancel(); reject(SendFailure.PREPARATION) }
        }
    }
}
