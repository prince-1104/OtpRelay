package com.prince.otprelay.history

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun validatesManualFieldsWithoutAcceptingMessagesOrCodesAsBankNames() {
        assertEquals("Example Bank", VerificationInput.bank(" Example Bank "))
        assertNull(VerificationInput.bank("482619"))
        assertNull(VerificationInput.bank("Example OTP 482619"))
        assertNull(VerificationInput.bank("Example|Bank"))
        assertEquals("1250.50", VerificationInput.amount("1250.5"))
        listOf("0", "-1", "1.234", "1,250", "1e3", "OTP 482619").forEach {
            assertNull(VerificationInput.amount(it))
        }
    }

    @Test fun parsesStrictManualDates() {
        assertNotNull(VerificationDates.parse("2026-10-19 19:30"))
        assertNull(VerificationDates.parse("2026-02-30 19:30"))
        assertNull(VerificationDates.parse("2026-10-19 25:30"))
        assertNull(VerificationDates.parse("not a date"))
    }

    @Test fun persistsAcrossRecreationThenVerifiesDeletesAndClears() = runTest {
        val file = folder.root.resolve("history.preferences_pb")
        fun store(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() },
            scope = scope
        )
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = HistoryRepository(store(scope))
            assertTrue(first.records.first().isEmpty())
            first.add("Example Bank", 1000, "1250.50", "INR")
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = store(scope)
            val second = HistoryRepository(dataStore)
            val record = second.records.first().single()
            assertEquals("Example Bank", record.bank)
            assertEquals(1000L, record.occurredAt)
            assertEquals("1250.50", record.amount)
            assertFalse(record.verified)
            assertEquals(setOf("manual_records"), dataStore.data.first().asMap().keys.map { it.name }.toSet())
            second.verified(record.id, true)
            assertTrue(second.records.first().single().verified)
            second.delete(record.id)
            assertTrue(second.records.first().isEmpty())
            second.add("Example Bank", 2000, null, "INR")
            assertNull(second.records.first().single().amount)
            second.clear()
            assertTrue(second.records.first().isEmpty())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun boundsHistoryAndRejectsInvalidWrites() = runTest {
        val repository = HistoryRepository(MemoryStore())
        repeat(201) { repository.add("Example Bank", it + 1L, null, "INR") }
        assertEquals(200, repository.records.first().size)
        assertEquals(201L, repository.records.first().first().occurredAt)
        assertEquals(2L, repository.records.first().last().occurredAt)
        try {
            repository.add("OTP 482619", 300, null, "INR")
            fail("Invalid bank must not be persisted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(200, repository.records.first().size)
    }

    @Test fun ignoresMalformedStoredMetadata() = runTest {
        val store = MemoryStore()
        store.updateData { mutablePreferencesOf(stringSetPreferencesKey("manual_records") to
            setOf("malformed", "bad-id|Example Bank|1000|1.00|INR|false")) }
        assertTrue(HistoryRepository(store).records.first().isEmpty())
    }
}
