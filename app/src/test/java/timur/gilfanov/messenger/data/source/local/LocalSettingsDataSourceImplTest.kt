package timur.gilfanov.messenger.data.source.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class LocalSettingsDataSourceImplTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val localSettingsDataSource: LocalSettingsDataSource by lazy {
        LocalSettingsDataSourceImpl(
            database = databaseRule.database,
            settingsDao = databaseRule.database.settingsDao(),
            logger = NoOpLogger(),
            defaultSettings = Settings(uiLanguage = UiLanguage.English),
        )
    }

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    // getSetting() tests
    @Test
    fun `getSetting returns Success when setting exists`() = runTest {
        // Given
        val entity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
        )
        databaseRule.database.settingsDao().upsert(entity)

        // When
        val result = localSettingsDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        // Then
        assertIs<ResultWithError.Success<SettingEntity, GetSettingError>>(result)
        assertEquals(testUserId.id.toString(), result.data.userId)
        assertEquals(SettingKey.UI_LANGUAGE.key, result.data.key)
        assertEquals(UiLanguage.English.toStorageValue(), result.data.value)
    }

    @Test
    fun `getSetting returns SettingNotFound when setting does not exist`() = runTest {
        // When
        val result = localSettingsDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        // Then
        assertIs<ResultWithError.Failure<SettingEntity, GetSettingError>>(result)
        assertEquals(GetSettingError.SettingNotFound, result.error)
    }

    // upsert(single) tests
    @Test
    fun `upsert inserts new setting successfully`() = runTest {
        // Given
        val entity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.German.toStorageValue(),
        )

        // When
        val result = localSettingsDataSource.upsert(entity)

        // Then
        assertIs<ResultWithError.Success<Unit, UpsertSettingError>>(result)

        // Verify in database
        val storedEntity = databaseRule.database.settingsDao().get(
            testUserId.id.toString(),
            SettingKey.UI_LANGUAGE.key,
        )
        assertNotNull(storedEntity)
        assertEquals(UiLanguage.German.toStorageValue(), storedEntity.value)
    }

    @Test
    fun `upsert updates existing setting successfully`() = runTest {
        // Given
        val originalEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
            localVersion = 1,
        )
        databaseRule.database.settingsDao().upsert(originalEntity)

        val updatedEntity = originalEntity.copy(
            value = UiLanguage.German.toStorageValue(),
            localVersion = 2,
        )

        // When
        val result = localSettingsDataSource.upsert(updatedEntity)

        // Then
        assertIs<ResultWithError.Success<Unit, UpsertSettingError>>(result)

        // Verify in database
        val storedEntity = databaseRule.database.settingsDao().get(
            testUserId.id.toString(),
            SettingKey.UI_LANGUAGE.key,
        )
        assertNotNull(storedEntity)
        assertEquals(UiLanguage.German.toStorageValue(), storedEntity.value)
        assertEquals(2, storedEntity.localVersion)
    }

    // upsert(batch) tests
    @Test
    fun `upsert batch inserts multiple settings successfully`() = runTest {
        // Given
        val entities = listOf(
            createSettingEntity(
                userId = testUserId,
                key = SettingKey.UI_LANGUAGE,
                value = UiLanguage.English.toStorageValue(),
            ),
            createSettingEntity(
                userId = testUserId,
                key = SettingKey.THEME,
                value = "DARK",
            ),
        )

        // When
        val result = localSettingsDataSource.upsert(entities)

        // Then
        assertIs<ResultWithError.Success<Unit, UpsertSettingError>>(result)

        // Verify all entities in database
        val allSettings = databaseRule.database.settingsDao().getAll(testUserId.id.toString())
        assertEquals(2, allSettings.size)
    }

    // transform() tests
    @Test
    fun `transform applies function and increments version`() = runTest {
        // Given
        val originalEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
            localVersion = 1,
            syncedVersion = 1,
            syncStatus = SyncStatus.SYNCED,
        )
        databaseRule.database.settingsDao().upsert(originalEntity)

        // When
        val result = localSettingsDataSource.transform(testUserId) { localSettings ->
            localSettings.copy(
                uiLanguage = localSettings.uiLanguage.copy(value = UiLanguage.German),
            )
        }

        // Then
        assertIs<ResultWithError.Success<Unit, TransformSettingError>>(result)

        // Verify transformation
        val updatedEntity = databaseRule.database.settingsDao().get(
            testUserId.id.toString(),
            SettingKey.UI_LANGUAGE.key,
        )
        assertNotNull(updatedEntity)
        assertEquals(UiLanguage.German.toStorageValue(), updatedEntity.value)
        assertEquals(2, updatedEntity.localVersion) // Incremented
        assertEquals(1, updatedEntity.syncedVersion) // Unchanged
        assertEquals(SyncStatus.PENDING, updatedEntity.syncStatus) // Changed to PENDING
    }

    @Test
    fun `transform does not increment version if value unchanged`() = runTest {
        // Given
        val originalEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
            localVersion = 3,
            syncedVersion = 3,
            syncStatus = SyncStatus.SYNCED,
        )
        databaseRule.database.settingsDao().upsert(originalEntity)

        // When - transform but don't change value
        val result = localSettingsDataSource.transform(testUserId) { localSettings ->
            localSettings // Return unchanged
        }

        // Then
        assertIs<ResultWithError.Success<Unit, TransformSettingError>>(result)

        // Verify no version increment
        val updatedEntity = databaseRule.database.settingsDao().get(
            testUserId.id.toString(),
            SettingKey.UI_LANGUAGE.key,
        )
        assertNotNull(updatedEntity)
        assertEquals(3, updatedEntity.localVersion) // Not incremented
        assertEquals(SyncStatus.SYNCED, updatedEntity.syncStatus) // Unchanged
    }

    @Test
    fun `transform returns SettingsNotFound when no settings exist`() = runTest {
        // When
        val result = localSettingsDataSource.transform(testUserId) { localSettings ->
            localSettings.copy(
                uiLanguage = localSettings.uiLanguage.copy(value = UiLanguage.German),
            )
        }

        // Then
        assertIs<ResultWithError.Failure<Unit, TransformSettingError>>(result)
        assertEquals(TransformSettingError.SettingsNotFound, result.error)
    }

    @Test
    fun `transform updates modifiedAt timestamp`() = runTest {
        // Given
        val originalEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
            modifiedAt = 1000L,
        )
        databaseRule.database.settingsDao().upsert(originalEntity)

        // When
        val result = localSettingsDataSource.transform(testUserId) { localSettings ->
            localSettings.copy(
                uiLanguage = localSettings.uiLanguage.copy(value = UiLanguage.German),
            )
        }

        // Then
        assertIs<ResultWithError.Success<Unit, TransformSettingError>>(result)

        // Verify modifiedAt was updated
        val updatedEntity = databaseRule.database.settingsDao().get(
            testUserId.id.toString(),
            SettingKey.UI_LANGUAGE.key,
        )
        assertNotNull(updatedEntity)
        assertTrue(updatedEntity.modifiedAt > 1000L, "modifiedAt should be updated")
    }

    // getUnsyncedSettings() tests
    @Test
    fun `getUnsyncedSettings returns only unsynced settings`() = runTest {
        // Given
        val syncedEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
            localVersion = 2,
            syncedVersion = 2, // Synced
        )
        val unsyncedEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.THEME,
            value = "DARK",
            localVersion = 3,
            syncedVersion = 1, // Not synced
        )
        databaseRule.database.settingsDao().upsert(syncedEntity)
        databaseRule.database.settingsDao().upsert(unsyncedEntity)

        // When
        val result = localSettingsDataSource.getUnsyncedSettings(testUserId)

        // Then
        assertIs<ResultWithError.Success<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertEquals(1, result.data.size)
        assertEquals(SettingKey.THEME.key, result.data[0].key)
    }

    @Test
    fun `getUnsyncedSettings returns empty list when all synced`() = runTest {
        // Given
        val syncedEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
            localVersion = 1,
            syncedVersion = 1,
        )
        databaseRule.database.settingsDao().upsert(syncedEntity)

        // When
        val result = localSettingsDataSource.getUnsyncedSettings(testUserId)

        // Then
        assertIs<ResultWithError.Success<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `getUnsyncedSettings returns empty list when no settings exist`() = runTest {
        // When
        val result = localSettingsDataSource.getUnsyncedSettings(testUserId)

        // Then
        assertIs<ResultWithError.Success<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `getUnsyncedSettings filters by userId and does not return other users settings`() =
        runTest {
            // Given
            val user1Unsynced = createSettingEntity(
                userId = testUserId,
                key = SettingKey.UI_LANGUAGE,
                value = UiLanguage.English.toStorageValue(),
                localVersion = 2,
                syncedVersion = 1,
            )
            val user2Unsynced = createSettingEntity(
                userId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                key = SettingKey.THEME,
                value = "DARK",
                localVersion = 3,
                syncedVersion = 1,
            )
            databaseRule.database.settingsDao().upsert(user1Unsynced)
            databaseRule.database.settingsDao().upsert(user2Unsynced)

            // When
            val result = localSettingsDataSource.getUnsyncedSettings(testUserId)

            // Then
            assertIs<ResultWithError.Success<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
            assertEquals(1, result.data.size)
            assertEquals(SettingKey.UI_LANGUAGE.key, result.data[0].key)
            assertEquals(testUserId.id.toString(), result.data[0].userId)
        }

    @Suppress("LongParameterList")
    private fun createSettingEntity(
        userId: UserId,
        key: SettingKey,
        value: String,
        localVersion: Int = 1,
        syncedVersion: Int = 0,
        serverVersion: Int = 0,
        modifiedAt: Long = 0L,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ): SettingEntity = SettingEntity(
        userId = userId.id.toString(),
        key = key.key,
        value = value,
        localVersion = localVersion,
        syncedVersion = syncedVersion,
        serverVersion = serverVersion,
        modifiedAt = modifiedAt,
        syncStatus = syncStatus,
    )
}
