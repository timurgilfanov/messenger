package timur.gilfanov.messenger.data.source.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class LocalSettingsDataSourceImplObserveTest {

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

    private val testUserKey = UserKey("user-key-1")

    @Test
    fun `observe emits NoSettings when database is empty`() = runTest {
        // When
        localSettingsDataSource.observe(testUserKey).test {
            // Then
            val emission = awaitItem()
            assertIs<ResultWithError.Failure<LocalSettings, GetSettingsLocalDataSourceError>>(
                emission,
            )
            assertEquals(GetSettingsLocalDataSourceError.NoSettings, emission.error)
        }
    }

    @Test
    fun `observe emits Success with LocalSettings when entities exist`() = runTest {
        // Given
        val entity = createSettingEntity(
            userKey = testUserKey,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
        )
        databaseRule.database.settingsDao().upsert(entity)

        // When
        localSettingsDataSource.observe(testUserKey).test {
            // Then
            val emission = awaitItem()
            assertIs<ResultWithError.Success<LocalSettings, GetSettingsLocalDataSourceError>>(
                emission,
            )
            assertEquals(UiLanguage.English, emission.data.uiLanguage.value)
        }
    }

    @Test
    fun `observe maps SettingEntity to LocalSettings correctly`() = runTest {
        // Given
        val entity = createSettingEntity(
            userKey = testUserKey,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.German.toStorageValue(),
            localVersion = 5,
            syncedVersion = 3,
            serverVersion = 3,
            modifiedAt = 1234567890L,
        )
        databaseRule.database.settingsDao().upsert(entity)

        // When
        localSettingsDataSource.observe(testUserKey).test {
            // Then
            val emission = awaitItem()
            assertIs<ResultWithError.Success<LocalSettings, GetSettingsLocalDataSourceError>>(
                emission,
            )
            val localSettings = emission.data

            assertEquals(UiLanguage.German, localSettings.uiLanguage.value)
            assertEquals(5, localSettings.uiLanguage.localVersion)
            assertEquals(3, localSettings.uiLanguage.syncedVersion)
            assertEquals(3, localSettings.uiLanguage.serverVersion)
            assertEquals(
                Instant.fromEpochMilliseconds(1234567890L),
                localSettings.uiLanguage.modifiedAt,
            )
        }
    }

    @Test
    fun `observe emits updated settings when entity changes`() = runTest {
        // Given
        val initialEntity = createSettingEntity(
            userKey = testUserKey,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
        )
        databaseRule.database.settingsDao().upsert(initialEntity)

        // When
        localSettingsDataSource.observe(testUserKey).test {
            // Initial emission
            val firstEmission = awaitItem()
            assertIs<ResultWithError.Success<LocalSettings, GetSettingsLocalDataSourceError>>(
                firstEmission,
            )
            assertEquals(UiLanguage.English, firstEmission.data.uiLanguage.value)

            // Update entity
            val updatedEntity = initialEntity.copy(
                value = UiLanguage.German.toStorageValue(),
                localVersion = 2,
            )
            databaseRule.database.settingsDao().upsert(updatedEntity)

            // Then - should emit updated settings
            val secondEmission = awaitItem()
            assertIs<ResultWithError.Success<LocalSettings, GetSettingsLocalDataSourceError>>(
                secondEmission,
            )
            assertEquals(UiLanguage.German, secondEmission.data.uiLanguage.value)
        }
    }

    @Test
    fun `observe filters settings by userId`() = runTest {
        // Given
        val user1Key = UserKey("user-key-1")
        val user2Key = UserKey("user-key-2")
        val user1Entity = createSettingEntity(
            userKey = user1Key,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
        )
        val user2Entity = createSettingEntity(
            userKey = user2Key,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.German.toStorageValue(),
        )
        databaseRule.database.settingsDao().upsert(user1Entity)
        databaseRule.database.settingsDao().upsert(user2Entity)

        // When - observe for user1
        localSettingsDataSource.observe(user1Key).test {
            // Then
            val emission = awaitItem()
            assertIs<ResultWithError.Success<LocalSettings, GetSettingsLocalDataSourceError>>(
                emission,
            )
            assertEquals(UiLanguage.English, emission.data.uiLanguage.value)
        }
    }

    @Test
    fun `observe emits NoSettings when all entities are deleted`() = runTest {
        // Given
        val entity = createSettingEntity(
            userKey = testUserKey,
            key = SettingKey.UI_LANGUAGE,
            value = UiLanguage.English.toStorageValue(),
        )
        databaseRule.database.settingsDao().upsert(entity)

        // When
        localSettingsDataSource.observe(testUserKey).test {
            // First emission - settings exist
            val firstEmission = awaitItem()
            assertIs<ResultWithError.Success<LocalSettings, GetSettingsLocalDataSourceError>>(
                firstEmission,
            )

            // Delete all settings (simulated by clearing the database)
            databaseRule.database.clearAllTables()

            // Then - should emit NoSettings
            val secondEmission = awaitItem()
            assertIs<ResultWithError.Failure<LocalSettings, GetSettingsLocalDataSourceError>>(
                secondEmission,
            )
            assertEquals(GetSettingsLocalDataSourceError.NoSettings, secondEmission.error)
        }
    }

    @Suppress("LongParameterList")
    private fun createSettingEntity(
        userKey: UserKey,
        key: SettingKey,
        value: String,
        localVersion: Int = 1,
        syncedVersion: Int = 0,
        serverVersion: Int = 0,
        modifiedAt: Long = 0L,
    ): SettingEntity = SettingEntity(
        userKey = userKey.key,
        key = key.key,
        value = value,
        localVersion = localVersion,
        syncedVersion = syncedVersion,
        serverVersion = serverVersion,
        modifiedAt = modifiedAt,
    )
}
