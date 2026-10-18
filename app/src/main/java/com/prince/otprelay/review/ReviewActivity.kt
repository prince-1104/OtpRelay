package com.prince.otprelay.review

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
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

class ReviewActivity : ComponentActivity() {
    private val token by lazy {
        intent.data?.takeIf { it.scheme == "otprelay" && it.authority == "review" }?.lastPathSegment.orEmpty()
    }
    private val model: ReviewViewModel by viewModels { ReviewViewModel.Factory(applicationContext, token) }
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            OtpRelayTheme {
                val state by model.state.collectAsStateWithLifecycle()
                BackHandler { cancelReview() }
                ReviewScreen(if (unlocked) state else ReviewState(message = "Unlock your phone to review."),
                    onSend = { model.openDraft(::openSmsDraft) }, onCancel = ::cancelReview)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        unlocked = !getSystemService(KeyguardManager::class.java).isDeviceLocked
        if (unlocked) model.refresh() else model.hide()
    }

    override fun onPause() {
        unlocked = false
        model.hide()
        super.onPause()
    }

    private fun cancelReview() {
        CandidateSessions.discard(applicationContext, token)
        model.hide()
        finish()
    }

    private fun openSmsDraft(candidate: OtpCandidate): String? {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            getSystemService(KeyguardManager::class.java).isDeviceLocked) return "Return to the unlocked review screen to continue."
        if (CandidateSessions.find(token) !== candidate) return "This candidate is no longer available. Nothing was sent."
        val smsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            ?: return "No default SMS app is configured. Nothing was sent."
        // This is the ONLY payload handoff, following the user's explicit review-button tap.
        // Restrict the draft to the user's default SMS app. No SMS permission for sending.
        val draft = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", candidate.recipient, null))
            .setPackage(smsPackage).putExtra("sms_body", candidate.code)
        return try {
            startActivity(draft)
            finish()
            null
        } catch (_: ActivityNotFoundException) {
            "Your default SMS app cannot open this draft. Nothing was sent."
        } catch (_: SecurityException) {
            "Android blocked opening the SMS draft. Nothing was sent."
        }
    }
}
