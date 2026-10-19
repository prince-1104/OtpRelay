package com.prince.otprelay.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var beforeWrite: suspend () -> Unit = {}
        var writes = 0
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writes++
            beforeWrite()
            return transform(data.value).also { data.value = it }
        }
    }

    @Test fun duplicateTapsAreIgnoredWhileSaving() = runTest(dispatcher) {
        val store = Store()
        val gate = CompletableDeferred<Unit>()
        store.beforeWrite = { gate.await() }
        val model = HistoryViewModel(HistoryRepository(store))
        models.put("history", model)
        advanceUntilIdle()
        model.add("Example Bank", 1000, "25.00", "INR")
        model.add("Example Bank", 1000, "25.00", "INR")
        runCurrent()
        assertTrue(model.state.value.busy)
        assertEquals(1, store.writes)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.state.value.busy)
        assertEquals(1, model.state.value.records.size)
        assertEquals("Record saved on this device.", model.state.value.notice)
    }

    @Test fun failedWriteDoesNotClaimSuccessAndAllowsExplicitRetry() = runTest(dispatcher) {
        val store = Store()
        store.beforeWrite = { throw IOException("Synthetic write failure") }
        val model = HistoryViewModel(HistoryRepository(store))
        models.put("history", model)
        advanceUntilIdle()
        model.add("Example Bank", 1000, null, "INR")
        advanceUntilIdle()
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.records.isEmpty())
        assertEquals("Could not save this change. Try again.", model.state.value.notice)
        assertEquals(1, store.writes)
        store.beforeWrite = {}
        model.add("Example Bank", 1000, null, "INR")
        advanceUntilIdle()
        assertEquals(1, model.state.value.records.size)
        assertEquals("Record saved on this device.", model.state.value.notice)
    }
}
