package timur.gilfanov.messenger.data.source.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
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
        val result = localSettingsDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)

        // Then
        assertIs<ResultWithError.Success<TypedLocalSetting, GetSettingError>>(result)
        assertIs<TypedLocalSetting.UiLanguage>(result.data)
        assertEquals(UiLanguage.English, result.data.setting.value)
    }

    @Test
    fun `getSetting returns SettingNotFound when setting does not exist`() = runTest {
        // When
        val result = localSettingsDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)

        // Then
        assertIs<ResultWithError.Failure<TypedLocalSetting, GetSettingError>>(result)
        assertEquals(GetSettingError.SettingNotFound, result.error)
    }

    // upsert(single) tests
    @Test
    fun `upsert inserts new setting successfully`() = runTest {
        // Given
        val typedSetting = createTypedLocalSetting(
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.German,
        )

        // When
        val result = localSettingsDataSource.upsert(testUserId, typedSetting)

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

        val updatedSetting = createTypedLocalSetting(
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.German,
            localVersion = 2,
        )

        // When
        val result = localSettingsDataSource.upsert(testUserId, updatedSetting)

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

    @Test
    fun `transform increments localVersion when value changes`() = runTest {
        val initial = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1_000L,
        )
        databaseRule.database.settingsDao().upsert(initial)

        val result = localSettingsDataSource.transform(testUserId) { settings ->
            settings.copy(uiLanguage = settings.uiLanguage.copy(value = UiLanguage.German))
        }

        assertIs<ResultWithError.Success<Unit, TransformSettingError>>(result)
        val stored = databaseRule.database.settingsDao()
            .get(testUserId.id.toString(), SettingKey.UI_LANGUAGE.key)
        assertNotNull(stored)
        assertEquals(3, stored.localVersion)
        assertEquals(1, stored.syncedVersion)
        assertEquals(1, stored.serverVersion)
        assertTrue(stored.modifiedAt > initial.modifiedAt)
        assertEquals(UiLanguage.German.toStorageValue(), stored.value)
    }

    // upsert(batch) tests
    @Test
    fun `upsert batch inserts multiple settings successfully`() = runTest {
        // Given
        val settings = listOf(
            createTypedLocalSetting(
                key = SettingKey.UI_LANGUAGE,
                value = UiLanguage.English,
            ),
        )

        // When
        val result = localSettingsDataSource.upsert(testUserId, settings)

        // Then
        assertIs<ResultWithError.Success<Unit, UpsertSettingError>>(result)

        // Verify all entities in database
        val allSettings = databaseRule.database.settingsDao().getAll(testUserId.id.toString())
        assertEquals(1, allSettings.size)
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
    fun `getUnsyncedSettings returns unsynced settings`() = runTest {
        // Given
        val syncedEntity = createSettingEntity(
            userId = testUserId,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.German.toStorageValue(),
            localVersion = 2,
            syncedVersion = 1, // Unsynced
        )
        databaseRule.database.settingsDao().upsert(syncedEntity)

        // When
        val result = localSettingsDataSource.getUnsyncedSettings(testUserId)

        // Then
        assertIs<ResultWithError.Success<List<TypedLocalSetting>, *>>(result)
        assertEquals(1, result.data.size)
        assertIs<TypedLocalSetting.UiLanguage>(result.data[0])
        assertEquals(UiLanguage.German, result.data[0].setting.value)
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
        assertIs<ResultWithError.Success<List<TypedLocalSetting>, GetUnsyncedSettingsError>>(result)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `getUnsyncedSettings returns empty list when no settings exist`() = runTest {
        // When
        val result = localSettingsDataSource.getUnsyncedSettings(testUserId)

        // Then
        assertIs<ResultWithError.Success<List<TypedLocalSetting>, GetUnsyncedSettingsError>>(result)
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
                key = SettingKey.UI_LANGUAGE,
                value = UiLanguage.German.toStorageValue(),
                localVersion = 3,
                syncedVersion = 1,
            )
            databaseRule.database.settingsDao().upsert(user1Unsynced)
            databaseRule.database.settingsDao().upsert(user2Unsynced)

            // When
            val result = localSettingsDataSource.getUnsyncedSettings(testUserId)

            // Then
            assertIs<ResultWithError.Success<List<TypedLocalSetting>, GetUnsyncedSettingsError>>(
                result,
            )
            assertEquals(1, result.data.size)
            assertIs<TypedLocalSetting.UiLanguage>(result.data[0])
            assertEquals(UiLanguage.English, result.data[0].setting.value)
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
    ): SettingEntity = SettingEntity(
        userId = userId.id.toString(),
        key = key.key,
        value = value,
        localVersion = localVersion,
        syncedVersion = syncedVersion,
        serverVersion = serverVersion,
        modifiedAt = modifiedAt,
    )

    @Suppress("LongParameterList")
    private fun createTypedLocalSetting(
        key: SettingKey,
        value: UiLanguage,
        localVersion: Int = 1,
        syncedVersion: Int = 0,
        serverVersion: Int = 0,
        modifiedAt: Long = 0L,
    ): TypedLocalSetting = when (key) {
        SettingKey.UI_LANGUAGE -> TypedLocalSetting.UiLanguage(
            setting = LocalSetting(
                value = value,
                localVersion = localVersion,
                syncedVersion = syncedVersion,
                serverVersion = serverVersion,
                modifiedAt = Instant.fromEpochMilliseconds(modifiedAt),
            ),
        )
        else -> error("Unknown setting key: $key")
    }
}
