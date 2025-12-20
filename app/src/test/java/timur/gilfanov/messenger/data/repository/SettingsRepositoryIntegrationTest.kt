package timur.gilfanov.messenger.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceImpl
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithSettingsChangeSuccess
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithSettingsSyncConflict
import timur.gilfanov.messenger.data.source.remote.MockServerScenarios.respondWithSettingsSyncSuccess
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule
import timur.gilfanov.messenger.testutil.MainDispatcherRule

/**
 * Integration tests for SettingsRepository with real local and remote data sources.
 * Tests end-to-end flows using in-memory database and MockEngine to simulate server responses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class SettingsRepositoryIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private lateinit var repository: SettingsRepositoryImpl

    private val logger = TestLogger()

    private val testUserId = UserId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
    private val testDeviceId = DeviceId(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"))
    private val testIdentity = Identity(userId = testUserId, deviceId = testDeviceId)
    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")
    private val defaultSettings = Settings(uiLanguage = UiLanguage.English)

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val syncSchedulerStub = object : SettingsSyncScheduler {
        override fun scheduleSettingSync(userId: UserId, key: SettingKey) = Unit
        override fun schedulePeriodicSync() = Unit
    }

    // Observation Tests
    @Test
    fun `observeSettings return default settings when local settings missing and remote fails`() =
        runTest {
            // Given - Server returns error
            setupRepository(
                MockEngine { request ->
                    respond(
                        "Server error",
                        status = io.ktor.http.HttpStatusCode.InternalServerError,
                    )
                },
            )

            // When/Then - should get default settings after recovery
            repository.observeSettings(testIdentity).test {
                // First emission may be SettingsResetToDefaults or Success with defaults
                val result = awaitItem()
                // After recovery, we should eventually get settings (either default or recovered)
                when (result) {
                    is ResultWithError.Failure -> {
                        // SettingsResetToDefaults indicates recovery with defaults
                        assertIs<GetSettingsRepositoryError.SettingsResetToDefaults>(result.error)
                        // Next emission should be Success with defaults
                        val settingsResult = awaitItem()
                        assertIs<ResultWithError.Success<Settings, *>>(settingsResult)
                        assertEquals(UiLanguage.English, settingsResult.data.uiLanguage)
                    }

                    is ResultWithError.Success -> {
                        // Success with default settings
                        assertEquals(UiLanguage.English, result.data.uiLanguage)
                    }
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeSettings should return existing local settings`() = runTest {
        // Given - Pre-populate local settings
        setupRepository(
            MockEngine { respond("No requests expected") },
        )

        // Pre-insert settings into database
        val settingEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = "ui_language",
            value = "German",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When/Then
        repository.observeSettings(testIdentity).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result)
            assertEquals(UiLanguage.German, result.data.uiLanguage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // Change UI Language Tests
    @Test
    fun `changeUiLanguage should update local settings and schedule sync`() = runTest {
        // Given - Pre-populate local settings and mock successful change
        var syncScheduled = false
        val customSyncScheduler = object : SettingsSyncScheduler {
            override fun scheduleSettingSync(userId: UserId, key: SettingKey) {
                syncScheduled = true
            }

            override fun schedulePeriodicSync() = Unit
        }

        setupRepository(
            MockEngine { request ->
                when {
                    request.url.segments.contains("settings") -> {
                        respondWithSettingsChangeSuccess()
                    }

                    else -> respond("Unexpected request")
                }
            },
            syncScheduler = customSyncScheduler,
        )

        // Pre-insert settings
        val settingEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = "ui_language",
            value = "English",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When
        val result = repository.changeUiLanguage(testIdentity, UiLanguage.German)

        // Then
        assertIs<ResultWithError.Success<Unit, *>>(result)

        // Verify local settings updated
        repository.observeSettings(testIdentity).test {
            val settingsResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(settingsResult)
            assertEquals(UiLanguage.German, settingsResult.data.uiLanguage)
            cancelAndIgnoreRemainingEvents()
        }

        // Verify sync was scheduled
        assertEquals(true, syncScheduled)
    }

    // Sync Setting Tests
    @Test
    fun `syncSetting should sync successfully with server`() = runTest {
        // Given - Local setting needs sync
        setupRepository(
            MockEngine { request ->
                when {
                    request.url.segments.contains("sync") -> {
                        respondWithSettingsSyncSuccess(key = "ui_language", newVersion = 2)
                    }

                    else -> respond("Unexpected request")
                }
            },
        )

        // Pre-insert unsynced setting (localVersion > syncedVersion)
        val settingEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = "ui_language",
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When
        val result = repository.syncSetting(testIdentity, SettingKey.UI_LANGUAGE)

        // Then
        assertIs<ResultWithError.Success<Unit, *>>(result)

        // Verify local setting updated with new server version
        val updatedEntity = databaseRule.database.settingsDao()
            .get(testUserId.id.toString(), "ui_language")
        assertEquals(2, updatedEntity?.serverVersion)
        assertEquals(2, updatedEntity?.syncedVersion)
    }

    @Test
    fun `syncSetting should handle conflict by accepting server value`() = runTest {
        // Given - Conflict scenario
        setupRepository(
            MockEngine { request ->
                when {
                    request.url.segments.contains("sync") -> {
                        respondWithSettingsSyncConflict(
                            key = "ui_language",
                            serverValue = "German",
                            serverVersion = 2,
                            newVersion = 3,
                            serverModifiedAt = testTimestamp.toString(),
                        )
                    }

                    else -> respond("Unexpected request")
                }
            },
        )

        // Pre-insert local setting
        val settingEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = "ui_language",
            value = "English",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When
        val result = repository.syncSetting(testIdentity, SettingKey.UI_LANGUAGE)

        // Then - Sync should succeed even with conflict (LWW resolution)
        assertIs<ResultWithError.Success<Unit, *>>(result)

        // Verify local setting updated to server value
        val updatedEntity = databaseRule.database.settingsDao()
            .get(testUserId.id.toString(), "ui_language")
        assertEquals("German", updatedEntity?.value)
    }

    // Helper functions
    private fun setupRepository(
        mockEngine: MockEngine,
        syncScheduler: SettingsSyncScheduler = syncSchedulerStub,
    ) {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val localDataSource = LocalSettingsDataSourceImpl(
            database = databaseRule.database,
            settingsDao = databaseRule.database.settingsDao(),
            logger = logger,
            defaultSettings = defaultSettings,
        )

        val remoteDataSource = RemoteSettingsDataSourceImpl(
            httpClient = httpClient,
            logger = logger,
        )

        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            syncScheduler = syncScheduler,
            logger = logger,
            defaultSettings = defaultSettings,
        )
    }
}
