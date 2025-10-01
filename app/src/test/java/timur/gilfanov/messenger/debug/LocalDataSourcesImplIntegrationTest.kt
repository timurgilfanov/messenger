package timur.gilfanov.messenger.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSourceImpl
import timur.gilfanov.messenger.data.source.local.LocalSyncDataSourceImpl
import timur.gilfanov.messenger.data.source.local.datastore.SyncPreferences
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Category(Component::class)
class LocalDataSourcesImplIntegrationTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private var logger = TestLogger()

    private lateinit var syncDataStore: DataStore<Preferences>

    private lateinit var syncDataSource: LocalSyncDataSourceImpl
    private lateinit var debugDataSource: LocalDebugDataSourceImpl

    private fun setupDataSource(scope: CoroutineScope) {
        val context: Context = ApplicationProvider.getApplicationContext()
        syncDataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = {
                context.preferencesDataStoreFile(
                    "test_sync_${System.currentTimeMillis()}",
                )
            },
        )
        val debugDataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = {
                context.preferencesDataStoreFile(
                    "test_debug_${System.currentTimeMillis()}",
                )
            },
        )

        syncDataSource = LocalSyncDataSourceImpl(
            dataStore = syncDataStore,
            database = databaseRule.database,
            chatDao = databaseRule.chatDao,
            messageDao = databaseRule.messageDao,
            participantDao = databaseRule.participantDao,
            logger = logger,
        )

        debugDataSource = LocalDebugDataSourceImpl(
            database = databaseRule.database,
            debugDataStore = debugDataStore,
            syncDataStore = syncDataStore,
            logger = logger,
        )
    }

    @Test
    fun `when clear last sync timestamp then sync data source flow should emit`() = runTest {
        setupDataSource(backgroundScope)

        syncDataSource.lastSyncTimestamp.test {
            // Given
            syncDataStore.edit { preferences ->
                preferences[SyncPreferences.LAST_SYNC_TIMESTAMP] = 12345L
            }

            val initialItem = awaitItem()
            assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(initialItem)
            assertNull(initialItem.data)

            val initialItem2 = awaitItem()
            assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(initialItem2)
            assertNotNull(initialItem2.data)
            assertEquals(12345L, initialItem2.data.toEpochMilliseconds())
            expectNoEvents()

            // When
            debugDataSource.clearSyncTimestamp()

            // Then
            val updatedItem = awaitItem()
            assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(updatedItem)
            assertNull(updatedItem.data)
        }
    }
}
