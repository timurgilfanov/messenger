package timur.gilfanov.messenger.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.remote.SyncResult
import timur.gilfanov.messenger.data.worker.SyncOutcome
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError

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
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.update(entity)

        repository.observeSettings(identity).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, GetSettingsRepositoryError>>(result)
            assertEquals(UiLanguage.German, result.data.uiLanguage)
            assertEquals(false, result.data.metadata.isDefault)
            assertEquals(Instant.fromEpochMilliseconds(1000L), result.data.metadata.lastModifiedAt)
            assertEquals(Instant.fromEpochMilliseconds(1000L), result.data.metadata.lastSyncedAt)
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
        val existingEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "English",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.update(existingEntity)

        val result = repository.changeUiLanguage(identity, UiLanguage.German)

        assertIs<ResultWithError.Success<Unit, *>>(result)

        val updatedEntity = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity)
        assertEquals("German", updatedEntity.data.value)
        assertEquals(2, updatedEntity.data.localVersion)
        assertEquals(1, updatedEntity.data.syncedVersion)
        assertEquals(SyncStatus.PENDING, updatedEntity.data.syncStatus)
    }

    @Test
    fun `syncSetting returns Success when already synced`() = runTest {
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "English",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )
        localDataSource.update(entity)

        val outcome = repository.syncSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        assertEquals(SyncOutcome.Success, outcome)
    }

    @Test
    fun `syncSetting handles successful remote sync`() = runTest {
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.update(entity)

        remoteDataSource.setSyncBehavior { SyncResult.Success(newVersion = 2) }

        val outcome = repository.syncSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        assertEquals(SyncOutcome.Success, outcome)

        val updatedEntity = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity)
        assertEquals(2, updatedEntity.data.syncedVersion)
        assertEquals(2, updatedEntity.data.serverVersion)
        assertEquals(SyncStatus.SYNCED, updatedEntity.data.syncStatus)
    }

    @Test
    fun `syncSetting handles conflict with client wins`() = runTest {
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 3000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.update(entity)

        remoteDataSource.setSyncBehavior {
            SyncResult.Conflict(
                serverValue = "English",
                serverVersion = 1,
                newVersion = 3,
                serverModifiedAt = 2000L,
            )
        }

        val outcome = repository.syncSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        assertEquals(SyncOutcome.Success, outcome)

        val updatedEntity = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity)
        assertEquals("German", updatedEntity.data.value)
        assertEquals(2, updatedEntity.data.localVersion)
        assertEquals(2, updatedEntity.data.syncedVersion)
        assertEquals(3, updatedEntity.data.serverVersion)
        assertEquals(SyncStatus.SYNCED, updatedEntity.data.syncStatus)
    }

    @Test
    fun `syncSetting handles conflict with server wins and emits event`() = runTest {
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.update(entity)

        remoteDataSource.setSyncBehavior {
            SyncResult.Conflict(
                serverValue = "English",
                serverVersion = 1,
                newVersion = 3,
                serverModifiedAt = 3000L,
            )
        }

        repository.observeConflicts().test {
            val outcome = repository.syncSetting(testUserId, SettingKey.UI_LANGUAGE.key)

            assertEquals(SyncOutcome.Success, outcome)

            val conflict = awaitItem()
            assertEquals(SettingKey.UI_LANGUAGE, conflict.settingKey)
            assertEquals("German", conflict.yourValue)
            assertEquals("English", conflict.acceptedValue)
            assertEquals(Instant.fromEpochMilliseconds(3000L), conflict.conflictedAt)

            val updatedEntity = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
            assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity)
            assertEquals("English", updatedEntity.data.value)
            assertEquals(3, updatedEntity.data.localVersion)
            assertEquals(3, updatedEntity.data.syncedVersion)
            assertEquals(3, updatedEntity.data.serverVersion)
            assertEquals(3000L, updatedEntity.data.modifiedAt)
            assertEquals(SyncStatus.SYNCED, updatedEntity.data.syncStatus)
        }
    }

    @Test
    fun `syncSetting handles error from remote`() = runTest {
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.update(entity)

        remoteDataSource.setSyncBehavior { SyncResult.Error("Network error") }

        val outcome = repository.syncSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        assertEquals(SyncOutcome.Retry, outcome)

        val updatedEntity = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity)
        assertEquals(SyncStatus.FAILED, updatedEntity.data.syncStatus)
    }

    @Test
    fun `syncAllPendingSettings returns Success when no unsynced settings`() = runTest {
        val outcome = repository.syncAllPendingSettings()

        assertEquals(SyncOutcome.Success, outcome)
    }

    @Test
    fun `syncAllPendingSettings syncs multiple settings`() = runTest {
        val entity1 = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        val entity2 = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.THEME.key,
            value = "Dark",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.update(entity1)
        localDataSource.update(entity2)

        remoteDataSource.setSyncBehavior { request ->
            SyncResult.Success(newVersion = request.clientVersion + 1)
        }

        val outcome = repository.syncAllPendingSettings()

        assertEquals(SyncOutcome.Success, outcome)

        val updatedEntity1 = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity1)
        assertEquals(2, updatedEntity1.data.syncedVersion)
        assertEquals(SyncStatus.SYNCED, updatedEntity1.data.syncStatus)

        val updatedEntity2 = localDataSource.getSetting(testUserId, SettingKey.THEME.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity2)
        assertEquals(1, updatedEntity2.data.syncedVersion)
        assertEquals(SyncStatus.SYNCED, updatedEntity2.data.syncStatus)
    }

    @Test
    fun `syncAllPendingSettings returns Retry when any setting fails`() = runTest {
        val entity1 = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.PENDING,
        )
        val entity2 = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.THEME.key,
            value = "Dark",
            localVersion = 1,
            syncedVersion = 0,
            serverVersion = 0,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.PENDING,
        )
        localDataSource.update(entity1)
        localDataSource.update(entity2)

        remoteDataSource.setSyncBehavior { request ->
            if (request.key == SettingKey.THEME.key) {
                SyncResult.Error("Network error")
            } else {
                SyncResult.Success(newVersion = request.clientVersion + 1)
            }
        }

        val outcome = repository.syncAllPendingSettings()

        assertEquals(SyncOutcome.Retry, outcome)

        val updatedEntity1 = localDataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity1)
        assertEquals(SyncStatus.SYNCED, updatedEntity1.data.syncStatus)

        val updatedEntity2 = localDataSource.getSetting(testUserId, SettingKey.THEME.key)
        assertIs<ResultWithError.Success<SettingEntity, *>>(updatedEntity2)
        assertEquals(SyncStatus.FAILED, updatedEntity2.data.syncStatus)
    }

    @Test
    fun `applyRemoteSettings returns Success`() = runTest {
        val settings = Settings(
            uiLanguage = UiLanguage.German,
            metadata = timur.gilfanov.messenger.domain.entity.user.SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(1000L),
                lastSyncedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val result = repository.applyRemoteSettings(identity, settings)

        assertIs<ResultWithError.Success<Unit, *>>(result)
    }

    @Test
    fun `syncLocalToRemote returns Success`() = runTest {
        val settings = Settings(
            uiLanguage = UiLanguage.German,
            metadata = timur.gilfanov.messenger.domain.entity.user.SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(1000L),
                lastSyncedAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val result = repository.syncLocalToRemote(identity, settings)

        assertIs<ResultWithError.Success<Unit, *>>(result)
    }

    // TODO Test changing absent setting and then sync it with the remote
}
