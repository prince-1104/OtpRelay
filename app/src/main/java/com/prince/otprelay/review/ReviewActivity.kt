package com.prince.otprelay.review

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prince.otprelay.ui.theme.OtpRelayTheme
import com.prince.otprelay.sending.ConfirmedSmsSender
import com.prince.otprelay.sending.SendPermissionSection
import com.prince.otprelay.sending.SendSessions

class ReviewActivity : ComponentActivity() {
    private val token by lazy {
        intent.data?.takeIf { it.scheme == "otprelay" && it.authority == "review" }?.lastPathSegment.orEmpty()
    }
    private val model: ReviewViewModel by viewModels { ReviewViewModel.Factory(applicationContext, token) }
    private var unlocked by mutableStateOf(false)
    private var sendPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            OtpRelayTheme {
                val state by model.state.collectAsStateWithLifecycle()
                BackHandler { cancelReview() }
                ReviewScreen(if (unlocked) state else ReviewState(message = "Unlock your phone to review."),
                    onSend = { model.sendToDad(::confirmedAndResumed) }, onCancel = ::cancelReview,
                    sendPermissionGranted = sendPermission,
                    permissionContent = { if (unlocked) SendPermissionSection(this@ReviewActivity) { sendPermission = it } })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CandidateSessions.refresh(applicationContext)
        unlocked = !getSystemService(KeyguardManager::class.java).isDeviceLocked
        sendPermission = ConfirmedSmsSender.permissionGranted(this)
        if (unlocked) model.refresh() else model.hide()
    }

    override fun onPause() {
        unlocked = false
        model.hide()
        super.onPause()
    }

    private fun cancelReview() {
        // Leaving the screen cannot recall an already submitted SMS.
        if (!SendSessions.controller.state(token).inProgress) CandidateSessions.discard(applicationContext, token)
        model.hide()
        finish()
    }

    private fun confirmedAndResumed() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        !getSystemService(KeyguardManager::class.java).isDeviceLocked
}
