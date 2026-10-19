package com.prince.otprelay.sending

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SendPermissionSection(activity: Activity, onGrantedChanged: (Boolean) -> Unit) {
    var granted by remember { mutableStateOf(ConfirmedSmsSender.permissionGranted(activity)) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var rationale by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = ConfirmedSmsSender.permissionGranted(activity)
        requested = true
        rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS)
        // Permission grant NEVER resumes or initiates a send. A new Send to Dad tap is required.
        onGrantedChanged(granted)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ConfirmedSmsSender.permissionGranted(activity)
                rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS)
                onGrantedChanged(granted)
            }
        }
        owner.lifecycle.addObserver(observer)
        onGrantedChanged(granted)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SMS sending permission", style = MaterialTheme.typography.titleMedium)
            Text(if (granted) "Granted. Only Send to Dad submits an outgoing SMS. Carrier SMS charges may apply."
                else "Not granted. Sending is blocked. Allow SMS permission in Android Settings > Apps > OtpRelay > Permissions. Your installer or device may restrict this permission.")
            if (!granted && (!requested || rationale)) {
                OutlinedButton(onClick = { requested = true; request.launch(Manifest.permission.SEND_SMS) }) {
                    Text("Allow SMS sending")
                }
            }
            if (!granted) {
                OutlinedButton(onClick = {
                    try { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.packageName, null))) }
                    catch (_: ActivityNotFoundException) { error = "Open your phone’s Settings app to enable SMS permission." }
                }) { Text("Open app permissions") }
            }
            Text("Uses your phone’s configured default SMS SIM. For multiple SIMs, select a default SMS SIM in Android SIM settings first. OtpRelay never chooses one automatically.")
            OutlinedButton(onClick = {
                try { activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                catch (_: ActivityNotFoundException) { error = "Open your phone’s Settings app to configure the SMS SIM." }
            }) { Text("Open SIM / network settings") }
            error?.let { Text(it) }
        }
    }
}
