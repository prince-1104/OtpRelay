package com.prince.otprelay.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okio.Path.Companion.toPath

class SettingsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    // Explicit storage is necessary: createWithPath also selects FileStorage in
    // the Android artifact, whose rename cannot replace an existing Windows file.
    private fun createStore(scope: CoroutineScope, file: File) = PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() },
        scope = scope
    )

    @Test fun defaultsAreEmptyAndDetectionIsOff() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = createStore(scope, temporaryFolder.root.resolve("defaults.preferences_pb"))
            assertEquals(RelaySettings(), SettingsRepository(store).settings.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun savesNormalizedSettingsAcrossStoreRecreationAndRemovesSenders() = runTest {
        val file = temporaryFolder.root.resolve("persist.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = createStore(scope, file)
        try {
            SettingsRepository(firstStore).save(RelaySettings("0044 20 7946 0018", setOf("vm-hdfcbk", "567890"), true))
            assertEquals(setOf("trusted_recipient", "approved_senders", "detection_enabled"),
                firstStore.data.first().asMap().keys.map { it.name }.toSet())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = SettingsRepository(createStore(scope, file))
            assertEquals(RelaySettings("+442079460018", setOf("VM-HDFCBK", "567890"), true), repository.settings.first())
            repository.save(RelaySettings("+442079460018", setOf("567890"), false))
            assertEquals(setOf("567890"), repository.settings.first().approvedSenders)
            assertFalse(repository.settings.first().detectionEnabled)
            repository.save(RelaySettings())
            assertEquals(RelaySettings(), repository.settings.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun invalidSaveDoesNotOverwriteExistingSettings() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = SettingsRepository(createStore(scope, temporaryFolder.root.resolve("invalid.preferences_pb")))
            val valid = RelaySettings("+442079460018", setOf("VM-HDFCBK"), true)
            repository.save(valid)
            try {
                repository.save(RelaySettings("bad", emptySet(), true))
                fail("Invalid settings should be rejected")
            } catch (_: IllegalArgumentException) { /* Expected. */ }
            assertEquals(valid, repository.settings.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
