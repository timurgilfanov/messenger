package timur.gilfanov.messenger.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
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
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
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

    private val testSession = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
    )
    private val testUserKey = testSession.toUserScopeKey()
    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")
    private val defaultSettings = Settings(uiLanguage = UiLanguage.English)

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val syncSchedulerStub = object : SettingsSyncScheduler {
        override fun scheduleSettingSync(userKey: UserScopeKey, key: SettingKey) = Unit
        override fun schedulePeriodicSync() = Unit
        override suspend fun cancelUserScopedJobs(userKey: UserScopeKey) = Unit
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

            repository.observeSettings(testUserKey).test {
                val failure = awaitItem()
                assertIs<ResultWithError.Failure<Settings, GetSettingsRepositoryError>>(failure)
                assertIs<GetSettingsRepositoryError.SettingsUnspecified>(failure.error)

                val success = awaitItem()
                assertIs<ResultWithError.Success<Settings, *>>(success)
                assertEquals(UiLanguage.English, success.data.uiLanguage)

                cancelAndIgnoreRemainingEvents()
            }

            assertNull(databaseRule.database.settingsDao().get(testUserKey.key, "ui_language"))
        }

    @Test
    fun `observeSettings should return existing local settings`() = runTest {
        // Given - Pre-populate local settings
        setupRepository(
            MockEngine { respond("No requests expected") },
        )

        // Pre-insert settings into database
        val settingEntity = SettingEntity(
            userKey = testUserKey.key,
            key = "ui_language",
            value = "German",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When/Then
        repository.observeSettings(testUserKey).test {
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
            override fun scheduleSettingSync(userKey: UserScopeKey, key: SettingKey) {
                syncScheduled = true
            }

            override fun schedulePeriodicSync() = Unit
            override suspend fun cancelUserScopedJobs(userKey: UserScopeKey) = Unit
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
            userKey = testUserKey.key,
            key = "ui_language",
            value = "English",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When
        val result = repository.changeUiLanguage(testUserKey, UiLanguage.German)

        // Then
        assertIs<ResultWithError.Success<Unit, *>>(result)

        // Verify local settings updated
        repository.observeSettings(testUserKey).test {
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
            userKey = testUserKey.key,
            key = "ui_language",
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When
        val result = repository.syncSetting(testUserKey, SettingKey.UI_LANGUAGE)

        // Then
        assertIs<ResultWithError.Success<Unit, *>>(result)

        // Verify local setting updated with new server version
        val updatedEntity = databaseRule.database.settingsDao()
            .get(testUserKey.key, "ui_language")
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
                            serverModifiedAt = testTimestamp,
                        )
                    }

                    else -> respond("Unexpected request")
                }
            },
        )

        // Pre-insert local setting
        val settingEntity = SettingEntity(
            userKey = testUserKey.key,
            key = "ui_language",
            value = "English",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = testTimestamp.toEpochMilliseconds(),
        )
        databaseRule.database.settingsDao().upsert(settingEntity)

        // When
        val result = repository.syncSetting(testUserKey, SettingKey.UI_LANGUAGE)

        // Then - Sync should succeed even with conflict (LWW resolution)
        assertIs<ResultWithError.Success<Unit, *>>(result)

        // Verify local setting updated to server value
        val updatedEntity = databaseRule.database.settingsDao()
            .get(testUserKey.key, "ui_language")
        assertEquals("German", updatedEntity?.value)
    }

    // Helper functions
    private fun setupRepository(
        mockEngine: MockEngine,
        syncScheduler: SettingsSyncScheduler = syncSchedulerStub,
    ) {
        mockEngine.config.dispatcher = Dispatchers.Unconfined

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
