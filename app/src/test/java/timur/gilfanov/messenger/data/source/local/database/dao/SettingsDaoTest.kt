package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class SettingsDaoTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val settingsDao: SettingsDao
        get() = databaseRule.database.settingsDao()

    private val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001").toString()

    @Test
    fun `getUnsynced returns settings where localVersion greater than syncedVersion`() = runTest {
        // Given
        val syncedSetting = SettingEntity(
            userId = testUserId,
            key = "ui_language",
            value = "English",
            localVersion = 2,
            syncedVersion = 2, // Same as local
            serverVersion = 2,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        val unsyncedSetting = SettingEntity(
            userId = testUserId,
            key = "theme",
            value = "DARK",
            localVersion = 3,
            syncedVersion = 1, // Less than local
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        settingsDao.upsert(syncedSetting)
        settingsDao.upsert(unsyncedSetting)

        // When
        val unsynced = settingsDao.getUnsynced(testUserId)

        // Then
        assertEquals(1, unsynced.size)
        assertEquals("theme", unsynced[0].key)
        assertEquals(3, unsynced[0].localVersion)
        assertEquals(1, unsynced[0].syncedVersion)
    }

    @Test
    fun `compound primary key enforces uniqueness per userId and key combination`() = runTest {
        // Given
        val user1Id = UUID.fromString("00000000-0000-0000-0000-000000000001").toString()
        val user2Id = UUID.fromString("00000000-0000-0000-0000-000000000002").toString()

        val user1Setting = SettingEntity(
            userId = user1Id,
            key = "ui_language",
            value = "English",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        val user2Setting = SettingEntity(
            userId = user2Id,
            key = "ui_language", // Same key, different user
            value = "German",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.SYNCED,
        )

        // When
        settingsDao.upsert(user1Setting)
        settingsDao.upsert(user2Setting)

        // Then - both settings should exist
        val user1Retrieved = settingsDao.get(user1Id, "ui_language")
        val user2Retrieved = settingsDao.get(user2Id, "ui_language")

        assertNotNull(user1Retrieved)
        assertNotNull(user2Retrieved)
        assertEquals("English", user1Retrieved.value)
        assertEquals("German", user2Retrieved.value)
    }

    @Test
    fun `upsert inserts new setting`() = runTest {
        // Given
        val setting = SettingEntity(
            userId = testUserId,
            key = "ui_language",
            value = "English",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )

        // When
        settingsDao.upsert(setting)

        // Then
        val retrieved = settingsDao.get(testUserId, "ui_language")
        assertNotNull(retrieved)
        assertEquals(testUserId, retrieved.userId)
        assertEquals("ui_language", retrieved.key)
        assertEquals("English", retrieved.value)
        assertEquals(1, retrieved.localVersion)
        assertEquals(0, retrieved.syncedVersion)
        assertEquals(0, retrieved.serverVersion)
        assertEquals(1000L, retrieved.modifiedAt)
        assertEquals(SyncStatus.SYNCED, retrieved.syncStatus)
    }

    @Test
    fun `upsert updates existing setting`() = runTest {
        // Given
        val originalSetting = SettingEntity(
            userId = testUserId,
            key = "ui_language",
            value = "English",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        settingsDao.upsert(originalSetting)

        val updatedSetting = originalSetting.copy(
            value = "German",
            localVersion = 2,
            modifiedAt = 2000L,
        )

        // When
        settingsDao.upsert(updatedSetting)

        // Then
        val retrieved = settingsDao.get(testUserId, "ui_language")
        assertNotNull(retrieved)
        assertEquals("German", retrieved.value)
        assertEquals(2, retrieved.localVersion)
    }

    @Test
    fun `observeAllByUser returns only settings for specified user`() = runTest {
        // Given
        val user1Id = UUID.fromString("00000000-0000-0000-0000-000000000001").toString()
        val user2Id = UUID.fromString("00000000-0000-0000-0000-000000000002").toString()

        val user1Setting = SettingEntity(
            userId = user1Id,
            key = "ui_language",
            value = "English",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        val user2Setting = SettingEntity(
            userId = user2Id,
            key = "ui_language",
            value = "German",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.SYNCED,
        )
        settingsDao.upsert(user1Setting)
        settingsDao.upsert(user2Setting)

        // When
        val allUser1Settings = settingsDao.getAll(user1Id)

        // Then
        assertEquals(1, allUser1Settings.size)
        assertEquals(user1Id, allUser1Settings[0].userId)
        assertEquals("English", allUser1Settings[0].value)
    }

    @Test
    fun `get returns null when setting does not exist`() = runTest {
        // When
        val retrieved = settingsDao.get(testUserId, "nonexistent_key")

        // Then
        assertNull(retrieved)
    }
}
