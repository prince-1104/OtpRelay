package com.prince.otprelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.prince.otprelay.settings.SettingsRepository
import com.prince.otprelay.settings.SettingsViewModel
import com.prince.otprelay.settings.SettingsScreen
import com.prince.otprelay.sms.DetectionStatusStore
import com.prince.otprelay.sms.SmsPermissionSection
import com.prince.otprelay.ui.theme.OtpRelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OtpRelayTheme {
                val model: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(SettingsRepository(applicationContext)))
                val state by model.uiState.collectAsStateWithLifecycle()
                val detectionStatus by DetectionStatusStore.status.collectAsStateWithLifecycle()
                SettingsScreen(state, model::recipientChanged, model::senderDraftChanged,
                    model::addSender, model::removeSender, model::detectionChanged, model::save, model::load) {
                    SmsPermissionSection(this@MainActivity, detectionStatus)
                }
            }
        }
    }
}
