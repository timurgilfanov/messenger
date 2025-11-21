package timur.gilfanov.messenger.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.local.TypedLocalSetting
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.data.source.remote.ChangeUiLanguageRemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceErrorV2
import timur.gilfanov.messenger.data.source.remote.RemoteSetting
import timur.gilfanov.messenger.data.source.remote.RemoteSettings
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteUserDataSourceError
import timur.gilfanov.messenger.data.source.remote.SyncBatchError
import timur.gilfanov.messenger.data.source.remote.SyncResult
import timur.gilfanov.messenger.data.source.remote.SyncSingleSettingError
import timur.gilfanov.messenger.data.source.remote.TypedSettingSyncRequest
import timur.gilfanov.messenger.data.source.remote.UpdateSettingsRemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError

private const val INVALID_UI_LANGUAGE = "abc"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SettingsRepositoryImplTest {

    private val testUserId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
    private val identity = Identity(
        userId = testUserId,
        deviceId = timur.gilfanov.messenger.domain.entity.user.DeviceId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        ),
    )

    private lateinit var localDataSource: LocalSettingsDataSourceFake
    private lateinit var remoteDataSource: RemoteSettingsDataSourceFake
    private lateinit var workManager: WorkManager
    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)

        localDataSource = LocalSettingsDataSourceFake()
        remoteDataSource = RemoteSettingsDataSourceFake(
            initialSettings = kotlinx.collections.immutable.persistentMapOf(),
            useRealTime = false,
        )
        testScope = CoroutineScope(UnconfinedTestDispatcher())

        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            workManager = workManager,
            logger = NoOpLogger(),
            defaultSettings = Settings(uiLanguage = UiLanguage.English),
        )
    }

    @Test
    fun `observeSettings returns default settings when no entities exist`() = runTest {
        repository.observeSettings(identity).test {
            val result1 = awaitItem()
            assertIs<ResultWithError.Failure<Settings, GetSettingsRepositoryError>>(result1)
            assertIs<GetSettingsRepositoryError.SettingsResetToDefaults>(result1.error)

            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, GetSettingsRepositoryError>>(result)
            assertEquals(UiLanguage.English, result.data.uiLanguage)
            // TODO Check settings is default. What we need to do with default status?
            // assertEquals(true, result.data.metadata.isDefault)
        }
    }

    @Test
    fun `observeSettings maps entities to domain settings`() = runTest {
        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.upsert(testUserId, setting)

        repository.observeSettings(identity).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, GetSettingsRepositoryError>>(result)
            assertEquals(UiLanguage.German, result.data.uiLanguage)
        }
    }

    // TODO Review this test when error handling in repository will be implemented
//    @Test
//    fun `changeUiLanguage creates new entity when none exists`() = runTest {
//        val result = repository.changeUiLanguage(identity, UiLanguage.German)
//
//        assertIs<ResultWithError.Success<Unit, *>>(result)
//
//        val savedEntity = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
//        assertIs<ResultWithError.Success<SettingEntity, *>>(savedEntity)
//        assertEquals("German", savedEntity.data.value)
//        assertEquals(1, savedEntity.data.localVersion)
//        assertEquals(0, savedEntity.data.syncedVersion)
//        assertEquals(SyncStatus.PENDING, savedEntity.data.syncStatus)
//    }

    @Test
    fun `changeUiLanguage updates existing entity`() = runTest {
        val existingSetting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.upsert(testUserId, existingSetting)

        val result = repository.changeUiLanguage(identity, UiLanguage.German)

        assertIs<ResultWithError.Success<Unit, *>>(result)

        val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
        assertEquals(UiLanguage.German, updatedSetting.data.setting.value)
        assertEquals(2, updatedSetting.data.setting.localVersion)
        assertEquals(1, updatedSetting.data.setting.syncedVersion)
        assertEquals(SyncStatus.PENDING, updatedSetting.data.setting.syncStatus)
    }

    @Test
    fun `syncSetting returns Success when already synced`() = runTest {
        val setting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.upsert(testUserId, setting)

        val outcome = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(outcome)
    }

    @Test
    fun `syncSetting handles successful remote sync`() = runTest {
        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior {
            ResultWithError.Success(SyncResult.Success(newVersion = 2))
        }

        val outcome = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(outcome)

        val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
        assertEquals(2, updatedSetting.data.setting.syncedVersion)
        assertEquals(2, updatedSetting.data.setting.serverVersion)
        assertEquals(SyncStatus.SYNCED, updatedSetting.data.setting.syncStatus)
    }

    @Test
    fun `syncSetting handles conflict with client wins`() = runTest {
        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 3000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior {
            ResultWithError.Success(
                SyncResult.Conflict(
                    serverValue = "English",
                    serverVersion = 1,
                    newVersion = 3,
                    serverModifiedAt = 2000L,
                ),
            )
        }

        val outcome = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(outcome)

        val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
        assertEquals(UiLanguage.German, updatedSetting.data.setting.value)
        assertEquals(2, updatedSetting.data.setting.localVersion)
        assertEquals(2, updatedSetting.data.setting.syncedVersion)
        assertEquals(3, updatedSetting.data.setting.serverVersion)
        assertEquals(SyncStatus.SYNCED, updatedSetting.data.setting.syncStatus)
    }

    @Test
    fun `syncSetting handles conflict with server wins and emits event`() = runTest {
        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior {
            ResultWithError.Success(
                SyncResult.Conflict(
                    serverValue = "English",
                    serverVersion = 1,
                    newVersion = 3,
                    serverModifiedAt = 3000L,
                ),
            )
        }

        repository.observeConflicts().test {
            val outcome = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

            assertIs<ResultWithError.Success<Unit, *>>(outcome)

            val conflict = awaitItem()
            assertEquals(SettingKey.UI_LANGUAGE, conflict.settingKey)
            assertEquals("German", conflict.localValue)
            assertEquals("English", conflict.acceptedValue)
            assertEquals(Instant.fromEpochMilliseconds(3000L), conflict.conflictedAt)

            val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
            assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
            assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
            assertEquals(UiLanguage.English, updatedSetting.data.setting.value)
            assertEquals(3, updatedSetting.data.setting.localVersion)
            assertEquals(3, updatedSetting.data.setting.syncedVersion)
            assertEquals(3, updatedSetting.data.setting.serverVersion)
            assertEquals(3000L, updatedSetting.data.setting.modifiedAt)
            assertEquals(SyncStatus.SYNCED, updatedSetting.data.setting.syncStatus)
        }
    }

    @Test
    fun `syncSetting handles error from remote`() = runTest {
        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior {
            ResultWithError.Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServerError,
                ),
            )
        }

        val outcome = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, *>>(outcome)

        val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
        assertEquals(SyncStatus.FAILED, updatedSetting.data.setting.syncStatus)
    }

    @Test
    fun `syncAllPendingSettings returns Success when no unsynced settings`() = runTest {
        val outcome = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Success<Unit, *>>(outcome)
    }

    @Test
    fun `syncAllPendingSettings syncs multiple settings`() = runTest {
        val setting1 = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting1)

        remoteDataSource.setSyncBehavior { request ->
            ResultWithError.Success(
                SyncResult.Success(
                    newVersion =
                    request.request.clientVersion + 1,
                ),
            )
        }

        val outcome = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Success<Unit, *>>(outcome)

        val updatedSetting1 = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting1)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting1.data)
        assertEquals(2, updatedSetting1.data.setting.syncedVersion)
        assertEquals(SyncStatus.SYNCED, updatedSetting1.data.setting.syncStatus)
    }

    @Test
    fun `syncAllPendingSettings returns Retry when any setting fails`() = runTest {
        val setting1 = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting1)

        remoteDataSource.setSyncBehavior { request ->
            ResultWithError.Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServerError,
                ),
            )
        }

        val outcome = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Failure<Unit, *>>(outcome)

        val updatedSetting1 = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting1)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting1.data)
        assertEquals(SyncStatus.FAILED, updatedSetting1.data.setting.syncStatus)
    }

    @Test
    fun `observeSettings triggers recovery with valid remote settings`() = runTest {
        val remoteWithData = RemoteSettingsDataSourceFake(
            initialSettings = kotlinx.collections.immutable.persistentMapOf(
                testUserId to Settings(uiLanguage = UiLanguage.German),
            ),
            useRealTime = false,
        )
        val repoWithRemote = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteWithData,
            workManager = workManager,
            logger = NoOpLogger(),
            defaultSettings = Settings(uiLanguage = UiLanguage.English),
        )

        repoWithRemote.observeSettings(identity).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, GetSettingsRepositoryError>>(result)
            assertEquals(UiLanguage.German, result.data.uiLanguage)

            val savedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
            assertIs<ResultWithError.Success<TypedLocalSetting, *>>(savedSetting)
            assertIs<TypedLocalSetting.UiLanguage>(savedSetting.data)
            assertEquals(UiLanguage.German, savedSetting.data.setting.value)
            assertEquals(1, savedSetting.data.setting.localVersion)
            assertEquals(1, savedSetting.data.setting.syncedVersion)
            assertEquals(1, savedSetting.data.setting.serverVersion)
            assertEquals(SyncStatus.SYNCED, savedSetting.data.setting.syncStatus)
        }
    }

    @Test
    fun `changeUiLanguage with no local settings triggers recovery then applies change`() =
        runTest {
            val remoteWithData = RemoteSettingsDataSourceFake(
                initialSettings = kotlinx.collections.immutable.persistentMapOf(
                    testUserId to Settings(uiLanguage = UiLanguage.English),
                ),
                useRealTime = false,
            )
            val repoWithRemote = SettingsRepositoryImpl(
                localDataSource = localDataSource,
                remoteDataSource = remoteWithData,
                workManager = workManager,
                logger = NoOpLogger(),
                defaultSettings = Settings(uiLanguage = UiLanguage.English),
            )

            val result = repoWithRemote.changeUiLanguage(identity, UiLanguage.German)

            assertIs<ResultWithError.Success<Unit, *>>(result)

            val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
            assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
            assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
            assertEquals(UiLanguage.German, updatedSetting.data.setting.value)
            assertEquals(2, updatedSetting.data.setting.localVersion)
            assertEquals(1, updatedSetting.data.setting.syncedVersion)
            assertEquals(SyncStatus.PENDING, updatedSetting.data.setting.syncStatus)
        }

    @Test
    fun `changeUiLanguage with no local and no remote creates default then applies change`() =
        runTest {
            val result = repository.changeUiLanguage(identity, UiLanguage.German)

            assertIs<ResultWithError.Success<Unit, *>>(result)

            val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
            assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
            assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
            assertEquals(UiLanguage.German, updatedSetting.data.setting.value)
            assertEquals(2, updatedSetting.data.setting.localVersion)
            assertEquals(0, updatedSetting.data.setting.syncedVersion)
            assertEquals(SyncStatus.PENDING, updatedSetting.data.setting.syncStatus)
        }

    @Test
    fun `WorkManager sync has network constraint`() = runTest {
        val existingSetting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.upsert(testUserId, existingSetting)

        repository.changeUiLanguage(identity, UiLanguage.German)

        val workInfos = workManager.getWorkInfosForUniqueWork(
            "sync_setting_${testUserId.id}_${SettingKey.UI_LANGUAGE.key}",
        ).get()

        val workSpec = workInfos[0]
        assertEquals(
            NetworkType.CONNECTED,
            workSpec.constraints.requiredNetworkType,
            "Work should require network connectivity",
        )
    }

    @Test
    fun `WorkManager sync has debounce delay`() = runTest {
        val existingSetting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.upsert(testUserId, existingSetting)

        repository.changeUiLanguage(identity, UiLanguage.German)

        val workInfos = workManager.getWorkInfosForUniqueWork(
            "sync_setting_${testUserId.id}_${SettingKey.UI_LANGUAGE.key}",
        ).get()

        val workSpec = workInfos[0]
        assertEquals(
            workSpec.state,
            WorkInfo.State.ENQUEUED,
            "Work should be enqueued (not running yet due to delay)",
        )
    }

    @Test
    fun `WorkManager sync uses REPLACE policy for debouncing`() = runTest {
        val existingSetting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.upsert(testUserId, existingSetting)

        repository.changeUiLanguage(identity, UiLanguage.German)
        val workInfos1 = workManager.getWorkInfosForUniqueWork(
            "sync_setting_${testUserId.id}_${SettingKey.UI_LANGUAGE.key}",
        ).get()
        val firstWorkId = workInfos1[0].id

        repository.changeUiLanguage(identity, UiLanguage.English)
        val workInfos2 = workManager.getWorkInfosForUniqueWork(
            "sync_setting_${testUserId.id}_${SettingKey.UI_LANGUAGE.key}",
        ).get()

        assertEquals(1, workInfos2.size, "Should only have one work request (replaced)")
        val secondWorkId = workInfos2[0].id
        assertTrue(
            firstWorkId != secondWorkId,
            "Second work should replace first (different IDs)",
        )
    }

    @Test
    fun `observeSettings triggers recovery with invalid server value falls back to English`() =
        runTest {
            val remoteWithInvalidValue = object : RemoteSettingsDataSource {
                override suspend fun get(
                    identity: Identity,
                ): ResultWithError<RemoteSettings, RemoteUserDataSourceError> {
                    val remoteSettings = RemoteSettings(
                        uiLanguage = RemoteSetting.InvalidValue(
                            rawValue = INVALID_UI_LANGUAGE,
                            serverVersion = 5,
                        ),
                    )
                    return ResultWithError.Success(remoteSettings)
                }

                override suspend fun changeUiLanguage(
                    identity: Identity,
                    language: UiLanguage,
                ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> =
                    ResultWithError.Success(Unit)

                override suspend fun put(
                    identity: Identity,
                    settings: Settings,
                ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> =
                    ResultWithError.Success(Unit)

                override suspend fun syncSingleSetting(
                    request: TypedSettingSyncRequest,
                ): ResultWithError<SyncResult, SyncSingleSettingError> = ResultWithError.Success(
                    SyncResult.Success(
                        newVersion =
                        request.request.clientVersion + 1,
                    ),
                )

                override suspend fun syncBatch(
                    requests: List<TypedSettingSyncRequest>,
                ): ResultWithError<Map<String, SyncResult>, SyncBatchError> =
                    ResultWithError.Success(emptyMap())
            }
            val repoWithInvalidRemote = SettingsRepositoryImpl(
                localDataSource = localDataSource,
                remoteDataSource = remoteWithInvalidValue,
                workManager = workManager,
                logger = NoOpLogger(),
                defaultSettings = Settings(uiLanguage = UiLanguage.English),
            )

            repoWithInvalidRemote.observeSettings(identity).test {
                val result = awaitItem()
                assertIs<ResultWithError.Success<Settings, GetSettingsRepositoryError>>(result)
                assertEquals(
                    UiLanguage.English,
                    result.data.uiLanguage,
                    "Should fall back to English for invalid server value",
                )

                val savedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
                assertIs<ResultWithError.Success<TypedLocalSetting, *>>(savedSetting)
                assertIs<TypedLocalSetting.UiLanguage>(savedSetting.data)
                assertEquals(UiLanguage.English, savedSetting.data.setting.value)
                assertEquals(5, savedSetting.data.setting.localVersion, "Should use server version")
                assertEquals(5, savedSetting.data.setting.syncedVersion, "Should mark as synced")
                assertEquals(5, savedSetting.data.setting.serverVersion)
                assertEquals(
                    SyncStatus.SYNCED,
                    savedSetting.data.setting.syncStatus,
                    "Should be SYNCED to acknowledge server version",
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `changeUiLanguage with invalid remote value recovers with fallback then applies change`() =
        runTest {
            val remoteWithInvalidValue = object : RemoteSettingsDataSource {
                override suspend fun get(
                    identity: Identity,
                ): ResultWithError<RemoteSettings, RemoteUserDataSourceError> {
                    val remoteSettings = RemoteSettings(
                        uiLanguage = RemoteSetting.InvalidValue(
                            rawValue = INVALID_UI_LANGUAGE,
                            serverVersion = 3,
                        ),
                    )
                    return ResultWithError.Success(remoteSettings)
                }

                override suspend fun changeUiLanguage(
                    identity: Identity,
                    language: UiLanguage,
                ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> =
                    ResultWithError.Success(Unit)

                override suspend fun put(
                    identity: Identity,
                    settings: Settings,
                ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> =
                    ResultWithError.Success(Unit)

                override suspend fun syncSingleSetting(
                    request: TypedSettingSyncRequest,
                ): ResultWithError<SyncResult, SyncSingleSettingError> = ResultWithError.Success(
                    SyncResult.Success(
                        newVersion =
                        request.request.clientVersion + 1,
                    ),
                )

                override suspend fun syncBatch(
                    requests: List<TypedSettingSyncRequest>,
                ): ResultWithError<Map<String, SyncResult>, SyncBatchError> =
                    ResultWithError.Success(emptyMap())
            }
            val repoWithInvalidRemote = SettingsRepositoryImpl(
                localDataSource = localDataSource,
                remoteDataSource = remoteWithInvalidValue,
                workManager = workManager,
                logger = NoOpLogger(),
                defaultSettings = Settings(uiLanguage = UiLanguage.English),
            )

            val result = repoWithInvalidRemote.changeUiLanguage(identity, UiLanguage.German)

            assertIs<ResultWithError.Success<Unit, ChangeLanguageRepositoryError>>(result)

            val savedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
            assertIs<ResultWithError.Success<TypedLocalSetting, *>>(savedSetting)
            assertIs<TypedLocalSetting.UiLanguage>(savedSetting.data)
            assertEquals(UiLanguage.German, savedSetting.data.setting.value)
            assertEquals(
                4,
                savedSetting.data.setting.localVersion,
                "Should increment from recovered version 3",
            )
            assertEquals(
                3,
                savedSetting.data.setting.syncedVersion,
                "Should still reference recovery sync",
            )
            assertEquals(3, savedSetting.data.setting.serverVersion)
            assertEquals(SyncStatus.PENDING, savedSetting.data.setting.syncStatus)
        }

    @Suppress("LongParameterList")
    private fun createTypedLocalSetting(
        value: UiLanguage,
        localVersion: Int = 1,
        syncedVersion: Int = 0,
        serverVersion: Int = 0,
        modifiedAt: Long = 0L,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ): TypedLocalSetting.UiLanguage = TypedLocalSetting.UiLanguage(
        setting = timur.gilfanov.messenger.data.source.local.LocalSetting(
            value = value,
            localVersion = localVersion,
            syncedVersion = syncedVersion,
            serverVersion = serverVersion,
            modifiedAt = modifiedAt,
            syncStatus = syncStatus,
        ),
    )
}
