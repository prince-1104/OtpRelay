package com.prince.otprelay.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.util.UUID

private val Context.verificationHistory by preferencesDataStore(name = "verification_history")

/** User-entered metadata only. No SMS, candidate, recipient or OTP is accepted. */
data class VerificationRecord(
    val id: String,
    val bank: String,
    val occurredAt: Long,
    val amount: String?,
    val currency: String,
    val verified: Boolean = false
)

object VerificationInput {
    fun bank(raw: String): String? = raw.trim().takeIf {
        it.length in 2..60 && it.any(Char::isLetter) &&
            it.all { c -> c.isLetter() || c in " .&'-" }
    }

    fun amount(raw: String): String? {
        if (!Regex("[0-9]{1,12}(\\.[0-9]{1,2})?").matches(raw.trim())) return null
        return raw.trim().toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
            ?.setScale(2)?.toPlainString()
    }

    fun valid(record: VerificationRecord): Boolean =
        runCatching { UUID.fromString(record.id) }.isSuccess && bank(record.bank) == record.bank &&
            record.occurredAt > 0 && record.currency in currencies &&
            (record.amount == null || amount(record.amount) == record.amount)

    val currencies = listOf("INR", "USD", "EUR", "GBP", "AED")
}

class HistoryRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.verificationHistory)

    val records = store.data.map { preferences -> decodeAll(preferences[KEY].orEmpty()) }

    suspend fun add(bank: String, occurredAt: Long, amount: String?, currency: String) {
        val record = VerificationRecord(UUID.randomUUID().toString(), bank, occurredAt, amount, currency)
        require(VerificationInput.valid(record)) { "Invalid verification record" }
        store.edit { preferences ->
            preferences[KEY] = (decodeAll(preferences[KEY].orEmpty()) + record)
                .sortedByDescending { it.occurredAt }.take(LIMIT).map(::encode).toSet()
        }
    }

    suspend fun verified(id: String, value: Boolean) = store.edit { preferences ->
        preferences[KEY] = decodeAll(preferences[KEY].orEmpty()).map {
            encode(if (it.id == id) it.copy(verified = value) else it)
        }.toSet()
    }

    suspend fun delete(id: String) = store.edit { preferences ->
        preferences[KEY] = decodeAll(preferences[KEY].orEmpty()).filterNot { it.id == id }.map(::encode).toSet()
    }

    suspend fun clear() { store.edit { it.remove(KEY) } }

    private fun encode(record: VerificationRecord) = listOf(record.id, record.bank,
        record.occurredAt.toString(), record.amount.orEmpty(), record.currency, record.verified.toString()).joinToString("|")

    private fun decodeAll(rows: Set<String>) = rows.mapNotNull { row ->
        runCatching {
            val fields = row.split('|')
            require(fields.size == 6)
            VerificationRecord(fields[0], fields[1], fields[2].toLong(), fields[3].ifEmpty { null },
                fields[4], fields[5].toBooleanStrict()).takeIf(VerificationInput::valid)
        }.getOrNull()
    }.sortedByDescending { it.occurredAt }.take(LIMIT)

    companion object {
        const val LIMIT = 200
        private val KEY = stringSetPreferencesKey("manual_records")
    }
}
