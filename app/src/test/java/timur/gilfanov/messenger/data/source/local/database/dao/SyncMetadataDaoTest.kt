package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.entity.SyncMetadataEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class SyncMetadataDaoTest {

    private lateinit var database: MessengerDatabase
    private lateinit var syncMetadataDao: SyncMetadataDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MessengerDatabase::class.java,
        ).allowMainThreadQueries().build()

        syncMetadataDao = database.syncMetadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and get sync metadata`() = runTest {
        // Given
        val metadata = createTestSyncMetadata()

        // When
        syncMetadataDao.updateSyncMetadata(metadata)
        val result = syncMetadataDao.getSyncMetadata()

        // Then
        assertNotNull(result)
        assertEquals(metadata.key, result.key)
        assertEquals(metadata.lastSyncTimestamp, result.lastSyncTimestamp)
    }

    @Test
    fun `update sync metadata replaces existing`() = runTest {
        // Given
        val metadata = createTestSyncMetadata()
        val updatedMetadata = metadata.copy(
            lastSyncTimestamp = Instant.fromEpochMilliseconds(3000000),
            syncStatus = SyncStatus.IDLE,
        )

        // When
        syncMetadataDao.updateSyncMetadata(metadata)
        syncMetadataDao.updateSyncMetadata(updatedMetadata)
        val result = syncMetadataDao.getSyncMetadata()

        // Then
        assertNotNull(result)
        assertEquals(SyncStatus.IDLE, result.syncStatus)
        assertNotNull(result.lastSyncTimestamp)
    }

    @Test
    fun `get last sync timestamp returns timestamp in millis`() = runTest {
        // Given
        val timestamp = Instant.fromEpochMilliseconds(2000000)
        val metadata = createTestSyncMetadata(lastSyncTimestamp = timestamp)

        // When
        syncMetadataDao.updateSyncMetadata(metadata)
        val result = syncMetadataDao.getLastSyncTimestamp()

        // Then
        assertNotNull(result)
        assertEquals(timestamp.toEpochMilliseconds(), result)
    }

    @Test
    fun `update last sync timestamp`() = runTest {
        // Given
        val metadata = createTestSyncMetadata()
        val newTimestamp = Instant.fromEpochMilliseconds(3000000).toEpochMilliseconds()
        val updatedAt = Instant.fromEpochMilliseconds(3100000).toEpochMilliseconds()

        // When
        syncMetadataDao.updateSyncMetadata(metadata)
        syncMetadataDao.updateLastSyncTimestamp(newTimestamp, updatedAt)
        val result = syncMetadataDao.getLastSyncTimestamp()

        // Then
        assertNotNull(result)
        assertEquals(newTimestamp, result)
    }

    @Test
    fun `update sync status`() = runTest {
        // Given
        val metadata = createTestSyncMetadata(syncStatus = SyncStatus.IDLE)
        val updatedAt = Instant.fromEpochMilliseconds(3200000).toEpochMilliseconds()

        // When
        syncMetadataDao.updateSyncMetadata(metadata)
        syncMetadataDao.updateSyncStatus(SyncStatus.IN_PROGRESS, updatedAt)
        val result = syncMetadataDao.getSyncMetadata()

        // Then
        assertNotNull(result)
        assertEquals(SyncStatus.IN_PROGRESS, result.syncStatus)
    }

    @Test
    fun `update sync error sets error message and status`() = runTest {
        // Given
        val metadata = createTestSyncMetadata()
        val errorMessage = "Network error occurred"
        val updatedAt = Instant.fromEpochMilliseconds(3300000).toEpochMilliseconds()

        // When
        syncMetadataDao.updateSyncMetadata(metadata)
        syncMetadataDao.updateSyncError(errorMessage, SyncStatus.ERROR, updatedAt)
        val result = syncMetadataDao.getSyncMetadata()

        // Then
        assertNotNull(result)
        assertEquals(SyncStatus.ERROR, result.syncStatus)
        assertEquals(errorMessage, result.lastError)
    }

    @Test
    fun `clear all sync metadata removes all data`() = runTest {
        // Given
        val metadata = createTestSyncMetadata()

        // When
        syncMetadataDao.updateSyncMetadata(metadata)
        syncMetadataDao.clearAllSyncMetadata()
        val result = syncMetadataDao.getSyncMetadata()

        // Then
        assertNull(result)
    }

    @Test
    fun `sync status transitions are properly stored`() = runTest {
        // Given
        val metadata = createTestSyncMetadata(syncStatus = SyncStatus.IDLE)
        val updatedAt = Instant.fromEpochMilliseconds(3400000).toEpochMilliseconds()

        // When & Then - Test status transitions
        syncMetadataDao.updateSyncMetadata(metadata)
        var result = syncMetadataDao.getSyncMetadata()
        assertEquals(SyncStatus.IDLE, result?.syncStatus)

        syncMetadataDao.updateSyncStatus(SyncStatus.IN_PROGRESS, updatedAt)
        result = syncMetadataDao.getSyncMetadata()
        assertEquals(SyncStatus.IN_PROGRESS, result?.syncStatus)

        syncMetadataDao.updateSyncStatus(SyncStatus.IDLE, updatedAt + 1000)
        result = syncMetadataDao.getSyncMetadata()
        assertEquals(SyncStatus.IDLE, result?.syncStatus)

        syncMetadataDao.updateSyncStatus(SyncStatus.ERROR, updatedAt + 2000)
        result = syncMetadataDao.getSyncMetadata()
        assertEquals(SyncStatus.ERROR, result?.syncStatus)
    }

    @Test
    fun `get sync metadata returns null when no data exists`() = runTest {
        // When
        val result = syncMetadataDao.getSyncMetadata()

        // Then
        assertNull(result)
    }

    private fun createTestSyncMetadata(
        syncStatus: SyncStatus = SyncStatus.IDLE,
        lastSyncTimestamp: kotlinx.datetime.Instant? = Instant.fromEpochMilliseconds(1000000),
    ) = SyncMetadataEntity(
        key = "last_sync",
        lastSyncTimestamp = lastSyncTimestamp,
        syncStatus = syncStatus,
        lastError = null,
        updatedAt = Instant.fromEpochMilliseconds(1100000),
    )
}
