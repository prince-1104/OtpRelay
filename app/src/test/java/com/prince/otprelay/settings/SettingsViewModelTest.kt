package com.prince.otprelay.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private class TestStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var failWrites = false
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("Test failure")
            return transform(data.value).also { data.value = it }
        }
    }

    @Test fun requiresAddingSenderDraftAndRejectsDuplicates() = runTest(dispatcher) {
        val store = TestStore()
        val model = SettingsViewModel(SettingsRepository(store))
        models.put("settings", model)
        advanceUntilIdle()
        model.recipientChanged("+44 20 7946 0018")
        model.senderDraftChanged("vm-hdfcbk")
        model.save()
        assertNotNull(model.uiState.value.senderError)
        assertTrue(store.data.value.asMap().isEmpty())
        model.addSender()
        model.senderDraftChanged("VM-HDFCBK")
        model.addSender()
        assertNotNull(model.uiState.value.senderError)
        assertEquals(1, model.uiState.value.senders.size)
        model.senderDraftChanged("")
        model.detectionChanged(true)
        model.save()
        advanceUntilIdle()
        assertEquals("+442079460018", model.uiState.value.recipient)
        assertTrue(SettingsRepository(store).settings.first().detectionEnabled)
    }

    @Test fun saveFailureRetainsEditsAndCanBeRetried() = runTest(dispatcher) {
        val store = TestStore()
        val model = SettingsViewModel(SettingsRepository(store))
        models.put("settings", model)
        advanceUntilIdle()
        model.recipientChanged("+44 20 7946 0018")
        store.failWrites = true
        model.save()
        advanceUntilIdle()
        assertFalse(model.uiState.value.saving)
        assertEquals("+44 20 7946 0018", model.uiState.value.recipient)
        assertTrue(model.uiState.value.message!!.contains("Could not save"))
        assertTrue(store.data.value.asMap().isEmpty())
        store.failWrites = false
        model.save()
        advanceUntilIdle()
        assertEquals("+442079460018", model.uiState.value.recipient)
        assertTrue(model.uiState.value.message!!.contains("saved"))
    }

    @Test fun loadFailureBlocksEditingAndSavingUntilExplicitRetrySucceeds() = runTest(dispatcher) {
        var failRead = true
        var writes = 0
        val store = object : DataStore<Preferences> {
            override val data get() = flow {
                if (failRead) throw IOException("Synthetic read failure")
                emit(emptyPreferences())
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                writes++
                return transform(emptyPreferences())
            }
        }
        val model = SettingsViewModel(SettingsRepository(store))
        models.put("settings", model)
        advanceUntilIdle()
        assertTrue(model.uiState.value.loadFailed)
        assertFalse(model.uiState.value.editable)
        model.recipientChanged("+442079460018")
        model.save()
        assertEquals(0, writes)
        assertEquals("", model.uiState.value.recipient)
        failRead = false
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.editable)
    }
}
