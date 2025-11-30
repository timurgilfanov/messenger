package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.data.source.local.GetSettingError
import timur.gilfanov.messenger.data.source.local.GetUnsyncedSettingsError
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.local.TypedLocalSetting
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.data.source.remote.ChangeUiLanguageRemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceErrorV2
import timur.gilfanov.messenger.data.source.remote.RemoteSetting
import timur.gilfanov.messenger.data.source.remote.RemoteSettings
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceStub
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
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncSettingRepositoryError

private const val INVALID_UI_LANGUAGE = "abc"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
@Suppress("LargeClass")
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
    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var testScope: CoroutineScope
    private val defaultSettings = Settings(uiLanguage = UiLanguage.English)

    private val syncSchedulerStub = object : SettingsSyncScheduler {
        override fun scheduleSettingSync(userId: UserId, key: SettingKey) = Unit
        override fun schedulePeriodicSync() = Unit
    }

    @Before
    fun setup() {
        localDataSource = LocalSettingsDataSourceFake()
        remoteDataSource = RemoteSettingsDataSourceFake(
            initialSettings = kotlinx.collections.immutable.persistentMapOf(),
            useRealTime = false,
        )
        testScope = CoroutineScope(UnconfinedTestDispatcher())

        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            syncScheduler = syncSchedulerStub,
            logger = NoOpLogger(),
            defaultSettings = defaultSettings,
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
            assertEquals(defaultSettings.uiLanguage, result.data.uiLanguage)
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
        val stub = RemoteSettingsDataSourceStub()
        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = stub,
            syncScheduler = syncSchedulerStub,
            logger = NoOpLogger(),
            defaultSettings = defaultSettings,
        )

        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        stub.setSyncSingleResponse(
            ResultWithError.Success(SyncResult.Success(newVersion = 2)),
        )

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
                    serverModifiedAt = Instant.fromEpochMilliseconds(3000L),
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
            assertEquals(
                Instant.fromEpochMilliseconds(3000L),
                updatedSetting.data.setting.modifiedAt,
            )
            assertEquals(SyncStatus.SYNCED, updatedSetting.data.setting.syncStatus)
        }
    }

    @Test
    fun `syncSetting handles error from remote`() = runTest {
        val stub = RemoteSettingsDataSourceStub()
        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = stub,
            syncScheduler = syncSchedulerStub,
            logger = NoOpLogger(),
            defaultSettings = defaultSettings,
        )

        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        stub.setSyncSingleResponse(
            ResultWithError.Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServerError,
                ),
            ),
        )

        val outcome = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, *>>(outcome)

        val updatedSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting.data)
        assertEquals(SyncStatus.FAILED, updatedSetting.data.setting.syncStatus)
    }

    @Test
    fun `syncSetting returns SettingNotFound when setting does not exist`() = runTest {
        localDataSource.setGetSettingBehavior(GetSettingError.SettingNotFound)

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.SettingNotFound>(result.error)
    }

    @Test
    fun `syncSetting returns RemoteSyncFailed when remote sync fails`() = runTest {
        val stub = RemoteSettingsDataSourceStub()
        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = stub,
            syncScheduler = syncSchedulerStub,
            logger = NoOpLogger(),
            defaultSettings = defaultSettings,
        )

        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        stub.setSyncSingleResponse(
            ResultWithError.Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable,
                ),
            ),
        )

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.RemoteSyncFailed>(result.error)
        assertIs<RepositoryError.Failed.NetworkNotAvailable>(result.error.error)
    }

    @Test
    fun `syncSetting returns TemporarilyUnavailable when local storage transiently fails`() =
        runTest {
            localDataSource.setGetSettingBehavior(GetSettingError.ConcurrentModificationError)

            val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

            assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
            assertIs<SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable>(
                result.error,
            )
        }

    @Test
    fun `syncSetting returns StorageFull when storage is full`() = runTest {
        localDataSource.setGetSettingBehavior(GetSettingError.StorageFull)

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.LocalStorageError.StorageFull>(result.error)
    }

    @Test
    fun `syncSetting returns Corrupted when database is corrupted`() = runTest {
        localDataSource.setGetSettingBehavior(GetSettingError.DatabaseCorrupted)

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.LocalStorageError.Corrupted>(result.error)
    }

    @Test
    fun `syncSetting returns AccessDenied when permissions denied`() = runTest {
        localDataSource.setGetSettingBehavior(GetSettingError.AccessDenied)

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.LocalStorageError.AccessDenied>(result.error)
    }

    @Test
    fun `syncSetting returns ReadOnly when database is read-only`() = runTest {
        localDataSource.setGetSettingBehavior(GetSettingError.ReadOnlyDatabase)

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.LocalStorageError.ReadOnly>(result.error)
    }

    @Test
    fun `syncSetting returns UnknownError with cause when unknown error occurs`() = runTest {
        val testCause = RuntimeException("Test database error")
        localDataSource.setGetSettingBehavior(GetSettingError.UnknownError(testCause))

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.LocalStorageError.UnknownError>(result.error)
        assertEquals(testCause, result.error.cause)
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
        val stub = RemoteSettingsDataSourceStub()
        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = stub,
            syncScheduler = syncSchedulerStub,
            logger = NoOpLogger(),
            defaultSettings = defaultSettings,
        )

        val setting1 = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting1)

        stub.setSyncBatchResponse(
            ResultWithError.Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServerError,
                ),
            ),
        )

        val outcome = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Failure<Unit, *>>(outcome)

        val updatedSetting1 = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(updatedSetting1)
        assertIs<TypedLocalSetting.UiLanguage>(updatedSetting1.data)
        assertEquals(SyncStatus.FAILED, updatedSetting1.data.setting.syncStatus)
    }

    @Test
    fun `syncAllPendingSettings returns RemoteSyncFailed when sync fails`() = runTest {
        val stub = RemoteSettingsDataSourceStub()
        repository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = stub,
            syncScheduler = syncSchedulerStub,
            logger = NoOpLogger(),
            defaultSettings = defaultSettings,
        )

        val setting = createTypedLocalSetting(
            value = UiLanguage.German,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        stub.setSyncBatchResponse(
            ResultWithError.Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServerError,
                ),
            ),
        )

        val result = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Failure<Unit, SyncAllSettingsRepositoryError>>(result)
        assertIs<SyncAllSettingsRepositoryError.RemoteSyncFailed>(result.error)
        assertIs<RepositoryError.Failed.ServiceDown>(result.error.error)
    }

    @Test
    fun `syncAllPendingSettings emits conflict event when server wins`() = runTest {
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
                    serverVersion = 2,
                    newVersion = 3,
                    serverModifiedAt = Instant.fromEpochMilliseconds(3000L),
                ),
            )
        }

        repository.observeConflicts().test {
            val result = repository.syncAllPendingSettings(identity)

            assertIs<ResultWithError.Success<Unit, *>>(result)

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
            assertEquals(SyncStatus.SYNCED, updatedSetting.data.setting.syncStatus)
        }
    }

    @Test
    fun `syncAllPendingSettings returns TemporarilyUnavailable when storage transiently fails`() =
        runTest {
            localDataSource.setGetUnsyncedBehavior(
                GetUnsyncedSettingsError.ConcurrentModificationError,
            )

            val result = repository.syncAllPendingSettings(identity)

            assertIs<ResultWithError.Failure<Unit, SyncAllSettingsRepositoryError>>(result)
            assertIs<SyncAllSettingsRepositoryError.LocalStorageError.TemporarilyUnavailable>(
                result.error,
            )
        }

    @Test
    fun `syncAllPendingSettings returns StorageFull when storage is full`() = runTest {
        localDataSource.setGetUnsyncedBehavior(GetUnsyncedSettingsError.StorageFull)

        val result = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Failure<Unit, SyncAllSettingsRepositoryError>>(result)
        assertIs<SyncAllSettingsRepositoryError.LocalStorageError.StorageFull>(result.error)
    }

    @Test
    fun `syncAllPendingSettings returns Corrupted when database is corrupted`() = runTest {
        localDataSource.setGetUnsyncedBehavior(GetUnsyncedSettingsError.DatabaseCorrupted)

        val result = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Failure<Unit, SyncAllSettingsRepositoryError>>(result)
        assertIs<SyncAllSettingsRepositoryError.LocalStorageError.Corrupted>(result.error)
    }

    @Test
    fun `syncAllPendingSettings returns AccessDenied when permissions denied`() = runTest {
        localDataSource.setGetUnsyncedBehavior(GetUnsyncedSettingsError.AccessDenied)

        val result = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Failure<Unit, SyncAllSettingsRepositoryError>>(result)
        assertIs<SyncAllSettingsRepositoryError.LocalStorageError.AccessDenied>(result.error)
    }

    @Test
    fun `syncAllPendingSettings returns ReadOnly when database is read-only`() = runTest {
        localDataSource.setGetUnsyncedBehavior(GetUnsyncedSettingsError.ReadOnlyDatabase)

        val result = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Failure<Unit, SyncAllSettingsRepositoryError>>(result)
        assertIs<SyncAllSettingsRepositoryError.LocalStorageError.ReadOnly>(result.error)
    }

    @Test
    fun `syncAllPendingSettings returns UnknownError with cause when unknown error occurs`() =
        runTest {
            val testCause = RuntimeException("Test database error")
            localDataSource.setGetUnsyncedBehavior(GetUnsyncedSettingsError.UnknownError(testCause))

            val result = repository.syncAllPendingSettings(identity)

            assertIs<ResultWithError.Failure<Unit, SyncAllSettingsRepositoryError>>(result)
            assertIs<SyncAllSettingsRepositoryError.LocalStorageError.UnknownError>(result.error)
            assertEquals(testCause, result.error.cause)
        }

    @Test
    fun `observeSettings triggers recovery with valid remote settings`() = runTest {
        val stub = RemoteSettingsDataSourceStub()
        stub.setGetResponse(
            ResultWithError.Success(
                RemoteSettings(
                    uiLanguage = RemoteSetting.Valid(
                        value = UiLanguage.German,
                        serverVersion = 1,
                    ),
                ),
            ),
        )
        val repoWithRemote = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = stub,
            syncScheduler = syncSchedulerStub,
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
            val stub = RemoteSettingsDataSourceStub()
            stub.setGetResponse(
                ResultWithError.Success(
                    RemoteSettings(
                        uiLanguage = RemoteSetting.Valid(
                            value = UiLanguage.English,
                            serverVersion = 1,
                        ),
                    ),
                ),
            )
            val repoWithRemote = SettingsRepositoryImpl(
                localDataSource = localDataSource,
                remoteDataSource = stub,
                syncScheduler = syncSchedulerStub,
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
                syncScheduler = syncSchedulerStub,
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
                syncScheduler = syncSchedulerStub,
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

    @Test
    fun `syncSetting accepts server conflict value`() = runTest {
        val currentTime = Instant.parse("2025-01-15T14:00:00Z")
        val futureTime = Instant.parse("2025-01-15T14:10:00Z")
        val setting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = currentTime.toEpochMilliseconds(),
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior {
            ResultWithError.Success(
                SyncResult.Conflict(
                    serverValue = "German",
                    serverVersion = 1,
                    newVersion = 2,
                    serverModifiedAt = futureTime,
                ),
            )
        }

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(result)
        val localSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(localSetting)
        assertIs<TypedLocalSetting.UiLanguage>(localSetting.data)
        assertEquals(UiLanguage.German, localSetting.data.setting.value)
        assertEquals(2, localSetting.data.setting.syncedVersion)
    }

    @Test
    fun `syncSetting handles concurrent modification on server success`() = runTest {
        val timestamp = Instant.parse("2025-01-15T10:00:00Z")
        val originalSetting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = timestamp.toEpochMilliseconds(),
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, originalSetting)

        remoteDataSource.setSyncBehavior {
            val modifiedSetting = createTypedLocalSetting(
                value = UiLanguage.German,
                localVersion = 3,
                syncedVersion = 1,
                serverVersion = 1,
                modifiedAt = timestamp.plus(1.seconds).toEpochMilliseconds(),
                syncStatus = SyncStatus.PENDING,
            )
            runBlocking { localDataSource.upsert(testUserId, modifiedSetting) }
            ResultWithError.Success(SyncResult.Success(newVersion = 2))
        }

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(result)
        val localSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(localSetting)
        assertIs<TypedLocalSetting.UiLanguage>(localSetting.data)
        assertEquals(UiLanguage.German, localSetting.data.setting.value)
        assertEquals(3, localSetting.data.setting.localVersion)
        assertEquals(2, localSetting.data.setting.syncedVersion)
        assertEquals(2, localSetting.data.setting.serverVersion)
        assertEquals(SyncStatus.PENDING, localSetting.data.setting.syncStatus)
    }

    @Test
    fun `syncSetting handles concurrent modification on server conflict`() = runTest {
        val timestamp = Instant.parse("2025-01-15T10:00:00Z")
        val originalSetting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = timestamp.toEpochMilliseconds(),
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, originalSetting)

        remoteDataSource.setSyncBehavior {
            val modifiedSetting = createTypedLocalSetting(
                value = UiLanguage.German,
                localVersion = 3,
                syncedVersion = 1,
                serverVersion = 1,
                modifiedAt = timestamp.plus(1.seconds).toEpochMilliseconds(),
                syncStatus = SyncStatus.PENDING,
            )
            runBlocking { localDataSource.upsert(testUserId, modifiedSetting) }
            ResultWithError.Success(
                SyncResult.Conflict(
                    serverValue = "French",
                    serverVersion = 2,
                    newVersion = 3,
                    serverModifiedAt = timestamp.plus(2.seconds),
                ),
            )
        }

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(result)
        val localSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertIs<ResultWithError.Success<TypedLocalSetting, *>>(localSetting)
        assertIs<TypedLocalSetting.UiLanguage>(localSetting.data)
        assertEquals(UiLanguage.German, localSetting.data.setting.value)
        assertEquals(3, localSetting.data.setting.localVersion)
        assertEquals(2, localSetting.data.setting.syncedVersion)
        assertEquals(3, localSetting.data.setting.serverVersion)
        assertEquals(SyncStatus.PENDING, localSetting.data.setting.syncStatus)
    }

    // todo one more setting need to be added for this test to make sense
    @Test
    fun `syncAllPendingSettings handles mixed success conflict and failure`() = runTest {
        val timestamp = Instant.parse("2025-01-15T10:00:00Z")

        val setting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = timestamp.toEpochMilliseconds(),
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior { request ->
            val expectedTimestamp = Instant.fromEpochMilliseconds(
                request.request.modifiedAt.toEpochMilliseconds(),
            )
            if (expectedTimestamp == timestamp) {
                ResultWithError.Success(SyncResult.Success(newVersion = 2))
            } else {
                ResultWithError.Failure(
                    RemoteUserDataSourceError.RemoteDataSource(
                        RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable,
                    ),
                )
            }
        }

        val result = repository.syncAllPendingSettings(identity)

        assertIs<ResultWithError.Success<Unit, *>>(result)
        val langSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
        assertEquals(
            SyncStatus.SYNCED,
            (langSetting as ResultWithError.Success).data.setting.syncStatus,
        )
    }

    // todo one more setting need to be added for this test to make sense
    @Test
    fun `syncAllPendingSettings with network failure mid-batch updates partial results`() =
        runTest {
            val timestamp = Instant.parse("2025-01-15T10:00:00Z")

            val setting = createTypedLocalSetting(
                value = UiLanguage.English,
                localVersion = 2,
                syncedVersion = 1,
                serverVersion = 1,
                modifiedAt = timestamp.toEpochMilliseconds(),
                syncStatus = SyncStatus.PENDING,
            )
            localDataSource.upsert(testUserId, setting)

            var callCount = 0
            remoteDataSource.setSyncBehavior { _ ->
                callCount++
                if (callCount == 1) {
                    ResultWithError.Success(SyncResult.Success(newVersion = 2))
                } else {
                    ResultWithError.Failure(
                        RemoteUserDataSourceError.RemoteDataSource(
                            RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable,
                        ),
                    )
                }
            }

            val result = repository.syncAllPendingSettings(identity)

            assertIs<ResultWithError.Success<Unit, *>>(result)
            val firstSetting = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE)
            assertEquals(
                SyncStatus.SYNCED,
                (firstSetting as ResultWithError.Success).data.setting.syncStatus,
            )
        }

    @Test
    fun `syncSetting marks FAILED when local storage fails after server accepts`() = runTest {
        val timestamp = Instant.parse("2025-01-15T10:00:00Z")
        val setting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = timestamp.toEpochMilliseconds(),
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior {
            ResultWithError.Success(SyncResult.Success(newVersion = 2))
        }

        localDataSource.setUpsertBehavior(
            timur.gilfanov.messenger.data.source.local.UpsertSettingError.DiskIOError,
        )

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable>(result.error)
    }

    @Test
    fun `syncSetting returns StorageFull when storage full during conflict resolution`() = runTest {
        val localTimestamp = Instant.parse("2025-01-15T10:00:00Z")
        val serverTimestamp = Instant.parse("2025-01-15T11:00:00Z")
        val setting = createTypedLocalSetting(
            value = UiLanguage.English,
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = localTimestamp.toEpochMilliseconds(),
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.upsert(testUserId, setting)

        remoteDataSource.setSyncBehavior {
            ResultWithError.Success(
                SyncResult.Conflict(
                    serverValue = "German",
                    serverVersion = 1,
                    newVersion = 2,
                    serverModifiedAt = serverTimestamp,
                ),
            )
        }

        localDataSource.setUpsertBehavior(
            timur.gilfanov.messenger.data.source.local.UpsertSettingError.StorageFull,
        )

        val result = repository.syncSetting(identity, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingRepositoryError>>(result)
        assertIs<SyncSettingRepositoryError.LocalStorageError.StorageFull>(result.error)
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
            modifiedAt = Instant.fromEpochMilliseconds(modifiedAt),
            syncStatus = syncStatus,
        ),
    )
}
