package com.prince.otprelay.review

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Watches only a visible activity. Never launches UI from a background receiver. */
internal class ForegroundUnlockObserver(
    private val activity: ComponentActivity,
    private val onUnlocked: () -> Unit
) : DefaultLifecycleObserver {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) refresh()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        try {
            ContextCompat.registerReceiver(activity, receiver, IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        } catch (_: SecurityException) {
            // Resume still refreshes normally if the device does not permit this observer.
        }
    }

    override fun onResume(owner: LifecycleOwner) = refresh()

    override fun onStop(owner: LifecycleOwner) {
        if (registered) {
            activity.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun refresh() {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !activity.getSystemService(KeyguardManager::class.java).isDeviceLocked) onUnlocked()
    }
}
