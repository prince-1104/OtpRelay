package com.prince.otprelay.sms

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SmsPermissionSection(activity: Activity, status: DetectionStatus) {
    fun isGranted() = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var rationale by remember { mutableStateOf(ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)) }
    var requestInProgress by rememberSaveable { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = isGranted()
        requested = true
        requestInProgress = false
        rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = isGranted()
                rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permission and detection status", style = MaterialTheme.typography.titleMedium)
            Text(if (granted) "Incoming SMS permission: granted." else "Incoming SMS permission: not granted. Detection cannot run.")
            Text("Allow Receive SMS to inspect newly delivered messages from approved senders when the saved detection switch is on. Inbox history and sent messages are never read.")
            if (!granted) {
                if (rationale) Text("SMS permission was denied. You can keep detection off, or allow it to inspect eligible incoming messages.")
                if (requested && !rationale) {
                    Text("Android is not offering another prompt. Permission may be restricted by your installer or device. You can check app settings; access is not guaranteed.")
                    OutlinedButton(onClick = {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", activity.packageName, null)))
                        } catch (_: ActivityNotFoundException) { settingsError = true }
                    }) { Text("Open app settings") }
                } else {
                    OutlinedButton(onClick = {
                        requestInProgress = true
                        requested = true
                        launcher.launch(Manifest.permission.RECEIVE_SMS)
                    }, enabled = !requestInProgress) { Text("Allow incoming SMS") }
                }
                if (settingsError) Text("App settings could not be opened. Use your phone’s Settings app.")
            }
            Text("Android can withhold or delay OTP broadcasts, including for hours. Permission does not guarantee bank OTP access. OtpRelay cannot know that an undelivered message existed and will not report it as detected.")
            Text(when (status) {
                DetectionStatus.WAITING -> "No eligible OTP has been observed in this app process. A missing broadcast does not prove that no OTP arrived."
                DetectionStatus.LIKELY_OTP_OBSERVED -> "An approved, delivered SMS with likely OTP context was observed in this app process. Its body was discarded; a candidate may be available briefly for review."
                DetectionStatus.INVALID_BROADCAST -> "A malformed incoming SMS broadcast was ignored. No detection was reported for it."
                DetectionStatus.PROCESSING_UNAVAILABLE -> "An incoming broadcast could not be processed. No detection was reported for it."
            })
            Text("Status is temporary and contains no SMS content. Only Send to Dad on the review screen can submit an SMS; nothing is sent automatically.")
            Text("Contacts, inbox history and sent-message history: no access requested.")
        }
    }
}
