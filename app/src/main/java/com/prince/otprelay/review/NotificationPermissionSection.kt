package com.prince.otprelay.review

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun NotificationPermissionSection(activity: Activity, candidateToken: String?) {
    var available by remember { mutableStateOf(OtpNotifications.available(activity)) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        available = OtpNotifications.available(activity)
        requested = true
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) available = OtpNotifications.available(activity)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(candidateToken) { available = OtpNotifications.available(activity) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OTP review notifications", style = MaterialTheme.typography.titleMedium)
            Text(if (available) "Notifications are allowed. Alerts contain no OTP, sender, or recipient."
                else "Notifications are off or blocked. Detection can continue; open this app to review a candidate before it expires.")
            if (!available) {
                if (Build.VERSION.SDK_INT >= 33 && (!requested || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))) {
                    Text("Allow private alerts so you can choose whether to review a detected code. Nothing is sent automatically.")
                    OutlinedButton(onClick = { requested = true; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow notifications")
                    }
                }
            }
            Text("For a popup banner, enable pop-on-screen/high importance for OTP review in notification settings. Android controls banners and Do Not Disturb; existing channel preferences are preserved.")
            OutlinedButton(onClick = {
                try {
                    activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
                } catch (_: ActivityNotFoundException) { error = "Open your phone’s Settings app to check notifications." }
            }) { Text("Notification settings") }
            Text("Candidates expire after two minutes and disappear if Android stops the app process. A new candidate replaces the previous one.")
            if (candidateToken != null) {
                Button(onClick = { activity.startActivity(OtpNotifications.reviewIntent(activity, candidateToken)) }) {
                    Text("Review latest candidate")
                }
            } else Text("No candidate is currently available to review.")
            error?.let { Text(it) }
        }
    }
}
