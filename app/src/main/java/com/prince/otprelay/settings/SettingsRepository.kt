package com.prince.otprelay.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One application-scoped DataStore instance per file, including across activity recreation.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class RelaySettings(
    val recipient: String = "",
    val approvedSenders: Set<String> = emptySet(),
    val detectionEnabled: Boolean = false
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<RelaySettings> = store.data.map { preferences ->
        RelaySettings(
            recipient = preferences[RECIPIENT].orEmpty(),
            approvedSenders = preferences[SENDERS]?.toSet() ?: emptySet(),
            detectionEnabled = preferences[ENABLED] ?: false
        )
    }

    suspend fun save(settings: RelaySettings) {
        require(validateSettings(settings.recipient, settings.approvedSenders, settings.detectionEnabled).isValid) {
            "Invalid settings"
        }
        val recipient = if (settings.recipient.isBlank()) "" else requireNotNull(PhoneNumbers.normalize(settings.recipient))
        val senders = settings.approvedSenders.map { requireNotNull(SenderIds.normalize(it)) }.toSet()
        // Only these three configuration fields are persisted. No SMS or OTP payload API.
        store.edit { preferences ->
            preferences[RECIPIENT] = recipient
            preferences[SENDERS] = senders
            preferences[ENABLED] = settings.detectionEnabled
        }
    }

    private companion object {
        val RECIPIENT = stringPreferencesKey("trusted_recipient")
        val SENDERS = stringSetPreferencesKey("approved_senders")
        val ENABLED = booleanPreferencesKey("detection_enabled")
    }
}
