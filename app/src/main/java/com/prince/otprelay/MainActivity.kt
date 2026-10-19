package com.prince.otprelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.app.KeyguardManager
import android.content.Intent
import com.prince.otprelay.history.HistoryActivity
import com.prince.otprelay.review.OtpNotifications
import com.prince.otprelay.review.ForegroundUnlockObserver
import com.prince.otprelay.review.ReviewPresentationGate
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import com.prince.otprelay.settings.SettingsRepository
import com.prince.otprelay.settings.SettingsViewModel
import com.prince.otprelay.settings.SettingsScreen
import com.prince.otprelay.sms.DetectionStatusStore
import com.prince.otprelay.sms.SmsPermissionSection
import com.prince.otprelay.review.CandidateSessions
import com.prince.otprelay.review.NotificationPermissionSection
import com.prince.otprelay.ui.theme.OtpRelayTheme

class MainActivity : ComponentActivity() {
    private val presentation = ReviewPresentationGate()
    override fun onResume() {
        super.onResume()
        CandidateSessions.refresh(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(ForegroundUnlockObserver(this, ::presentPendingReview))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                CandidateSessions.token.collect { presentPendingReview() }
            }
        }
        setContent {
            OtpRelayTheme {
                val model: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(SettingsRepository(applicationContext)))
                val state by model.uiState.collectAsStateWithLifecycle()
                val detectionStatus by DetectionStatusStore.status.collectAsStateWithLifecycle()
                val candidateToken by CandidateSessions.token.collectAsStateWithLifecycle()
                SettingsScreen(state, model::recipientChanged, model::senderDraftChanged,
                    model::addSender, model::removeSender, model::detectionChanged, model::save, model::load,
                    onHistory = { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) }) {
                    SmsPermissionSection(this@MainActivity, detectionStatus)
                    NotificationPermissionSection(this@MainActivity, candidateToken)
                }
            }
        }
    }

    private fun presentPendingReview() {
        CandidateSessions.refresh(applicationContext)
        val token = CandidateSessions.token.value
        if (presentation.take(token, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                !getSystemService(KeyguardManager::class.java).isDeviceLocked,
                token != null && CandidateSessions.find(token) != null)) {
            startActivity(OtpNotifications.reviewIntent(this, requireNotNull(token)))
        }
    }
}
