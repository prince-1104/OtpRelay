package com.prince.otprelay.review

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.prince.otprelay.R

object OtpNotifications {
    private const val CHANNEL = "otp_review"
    private const val ID = 1001
    private const val TEXT = "Possible bank OTP detected — tap to review."
    private var currentTap: PendingIntent? = null

    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @Synchronized fun show(context: Context, token: String) {
        try {
            post(context, token)
        } catch (_: SecurityException) {
            // Permission/channel access can be revoked during the operation.
        }
    }

    private fun post(context: Context, token: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "OTP review", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Private reminders to review a possible bank OTP"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        })
        cancel(context) // Also remove an older notification if permission/channel is now disabled.
        if (!available(context)) return
        val intent = reviewIntent(context, token)
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        currentTap = tap
        val publicNotification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("OtpRelay").setContentText(TEXT).build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("OtpRelay")
            .setContentText(TEXT).setStyle(NotificationCompat.BigTextStyle().bigText(TEXT))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicNotification)
            .setContentIntent(tap).setAutoCancel(true).setLocalOnly(true)
            .addAction(0, "Review & send", tap)
            .setTimeoutAfter(CandidateStore.LIFETIME_MS).build()
        try {
            // Recheck to handle revocation between constructing and posting the notification.
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(ID, notification)
            }
        } catch (_: SecurityException) { /* Permission denial keeps in-app review available. */ }
    }

    fun reviewIntent(context: Context, token: String) = Intent(context, ReviewActivity::class.java).apply {
        // Only an opaque random identifier enters the PendingIntent, never code/sender/body/recipient.
        data = Uri.Builder().scheme("otprelay").authority("review").appendPath(token).build()
    }

    @Synchronized fun cancel(context: Context) {
        currentTap?.cancel()
        currentTap = null
        NotificationManagerCompat.from(context).cancel(ID)
    }
}
