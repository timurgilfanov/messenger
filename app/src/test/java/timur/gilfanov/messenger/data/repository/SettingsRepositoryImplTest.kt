package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.data.source.local.GetSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.local.InsertSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSourceErrorV2
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.local.ResetSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.local.UpdateSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.remote.ChangeUiLanguageRemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceErrorV2
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteUserDataSourceError
import timur.gilfanov.messenger.data.source.remote.UpdateSettingsRemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.SettingsSource
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ApplyRemoteSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsChangeBackupError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncLocalToRemoteRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SettingsRepositoryImplTest {

    private val identity = Identity(
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        deviceId = DeviceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
    )

    private val defaultUiLanguage = UiLanguage.English

    private val defaultSettings =
        persistentMapOf(
            identity.userId to Settings(
                language = defaultUiLanguage,
                metadata = SettingsMetadata(
                    isDefault = false,
                    lastModifiedAt = Instant.fromEpochMilliseconds(1),
                    lastSyncedAt = Instant.fromEpochMilliseconds(1),
                ),
            ),
        )

    private val repository = SettingsRepositoryImpl(
        localDataSource = LocalSettingsDataSourceFake(defaultSettings),
        remoteDataSource = RemoteSettingsDataSourceFake(defaultSettings),
        logger = NoOpLogger(),
    )

    @Test
    fun `when change UI language then repository settings emit changes`() = runTest {
        repository.observeSettings(identity).test {
            val initialResult = awaitItem()
            assertIs<Success<Settings, *>>(initialResult)
            assertEquals(defaultUiLanguage, initialResult.data.language)
            expectNoEvents()

            val newUiLanguage = UiLanguage.German
            val changeLanguageResult = repository.changeLanguage(identity, newUiLanguage)
            assertIs<Success<Unit, *>>(changeLanguageResult)

            val updatedResult = awaitItem()
            assertIs<Success<Settings, *>>(updatedResult)
            assertEquals(newUiLanguage, updatedResult.data.language)
        }
    }

    @Test
    fun `observeSettings triggers recovery when SettingsNotFound`() = runTest {
        val emptyLocalDataSource = LocalSettingsDataSourceFake(persistentMapOf())
        val remoteSettings = Settings(
            language = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(100),
                lastSyncedAt = Instant.fromEpochMilliseconds(100),
            ),
        )
        val remoteDataSource = RemoteSettingsDataSourceFake(
            persistentMapOf(identity.userId to remoteSettings),
        )

        val repositoryWithEmptyLocal = SettingsRepositoryImpl(
            localDataSource = emptyLocalDataSource,
            remoteDataSource = remoteDataSource,
            logger = NoOpLogger(),
        )

        repositoryWithEmptyLocal.observeSettings(identity).test {
            val result = awaitItem()

            assertIs<Success<Settings, *>>(result)
            assertEquals(UiLanguage.German, result.data.language)
        }
    }

    @Test
    fun `observeSettings returns SettingsEmpty when LocalDataSource error occurs`() = runTest {
        val localDataSourceWithError = object : LocalSettingsDataSource {
            override fun observeSettings(
                userId: UserId,
            ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                Failure(
                    GetSettingsLocalDataSourceError.LocalDataSource(
                        LocalDataSourceErrorV2.ReadError(Exception("DataStore corruption")),
                    ),
                ),
            )

            override suspend fun updateSettings(
                userId: UserId,
                transform: (Settings) -> Settings,
            ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Success(Unit)

            override suspend fun insertSettings(
                userId: UserId,
                settings: Settings,
            ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> = Success(Unit)

            override suspend fun resetSettings(
                userId: UserId,
            ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
        }

        val repositoryWithError = SettingsRepositoryImpl(
            localDataSource = localDataSourceWithError,
            remoteDataSource = RemoteSettingsDataSourceFake(defaultSettings),
            logger = NoOpLogger(),
        )

        repositoryWithError.observeSettings(identity).test {
            val result = awaitItem()

            assertIs<Failure<*, GetSettingsRepositoryError>>(result)
            assertEquals(GetSettingsRepositoryError.SettingsEmpty, result.error)

            awaitComplete()
        }
    }

    @Test
    fun `performRecovery falls back to defaults when remote fetch fails`() = runTest {
        val emptyLocal = LocalSettingsDataSourceFake(persistentMapOf())
        val remoteWithError = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> = Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
                ),
            )

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
                ),
            )

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithFailedRemote = SettingsRepositoryImpl(
            localDataSource = emptyLocal,
            remoteDataSource = remoteWithError,
            logger = NoOpLogger(),
        )

        repositoryWithFailedRemote.observeSettings(identity).test {
            val firstResult = awaitItem()

            assertIs<Failure<*, GetSettingsRepositoryError>>(firstResult)
            assertEquals(GetSettingsRepositoryError.SettingsResetToDefaults, firstResult.error)

            val secondResult = awaitItem()
            assertIs<Success<Settings, *>>(secondResult)
            assertEquals(SettingsSource.DEFAULT, secondResult.data.metadata.source)
        }
    }

    @Test
    fun `applyRemoteSettings succeeds`() = runTest {
        val newSettings = Settings(
            language = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(200),
                lastSyncedAt = Instant.fromEpochMilliseconds(200),
            ),
        )

        val result = repository.applyRemoteSettings(identity, newSettings)

        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `syncLocalToRemote succeeds`() = runTest {
        val settingsToSync = Settings(
            language = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repository.syncLocalToRemote(identity, settingsToSync)

        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `changeLanguage returns Backup error when remote sync fails`() = runTest {
        val remoteWithError = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> =
                Success(defaultSettings[identity.userId]!!)

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
                ),
            )

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithBackupError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithError,
            logger = NoOpLogger(),
        )

        val result = repositoryWithBackupError.changeLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        assertIs<ChangeLanguageRepositoryError.Backup>(result.error)
        assertIs<SettingsChangeBackupError.ChangeNotBackedUp.ServiceDown>(result.error.error)
    }

    @Test
    fun `changeLanguage returns LanguageNotChanged when LocalDataSource error occurs`() = runTest {
        val localDataSourceWithError = object : LocalSettingsDataSource {
            override fun observeSettings(
                userId: UserId,
            ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                Success(defaultSettings[userId]!!),
            )

            override suspend fun updateSettings(
                userId: UserId,
                transform: (Settings) -> Settings,
            ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Failure(
                UpdateSettingsLocalDataSourceError.LocalDataSource(
                    LocalDataSourceErrorV2.WriteError(Exception("Write failed")),
                ),
            )

            override suspend fun insertSettings(
                userId: UserId,
                settings: Settings,
            ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> = Success(Unit)

            override suspend fun resetSettings(
                userId: UserId,
            ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
        }

        val repositoryWithError = SettingsRepositoryImpl(
            localDataSource = localDataSourceWithError,
            remoteDataSource = RemoteSettingsDataSourceFake(defaultSettings),
            logger = NoOpLogger(),
        )

        val result = repositoryWithError.changeLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        assertIs<ChangeLanguageRepositoryError.LanguageNotChanged>(result.error)
        assertEquals(true, result.error.transient)
    }

    @Test
    fun `applyRemoteSettings fails when insert fails`() = runTest {
        val localDataSourceWithError = object : LocalSettingsDataSource {
            override fun observeSettings(
                userId: UserId,
            ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                Success(defaultSettings[userId]!!),
            )

            override suspend fun updateSettings(
                userId: UserId,
                transform: (Settings) -> Settings,
            ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Success(Unit)

            override suspend fun insertSettings(
                userId: UserId,
                settings: Settings,
            ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> = Failure(
                LocalDataSourceErrorV2.WriteError(Exception("Insert failed")),
            )

            override suspend fun resetSettings(
                userId: UserId,
            ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
        }

        val repositoryWithError = SettingsRepositoryImpl(
            localDataSource = localDataSourceWithError,
            remoteDataSource = RemoteSettingsDataSourceFake(defaultSettings),
            logger = NoOpLogger(),
        )

        val newSettings = Settings(
            language = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(200),
                lastSyncedAt = Instant.fromEpochMilliseconds(200),
            ),
        )

        val result = repositoryWithError.applyRemoteSettings(identity, newSettings)

        assertIs<Failure<*, ApplyRemoteSettingsRepositoryError>>(result)
        assertIs<ApplyRemoteSettingsRepositoryError.SettingsNotApplied>(result.error)
    }

    @Test
    fun `syncLocalToRemote fails when remote update fails`() = runTest {
        val remoteWithError = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> =
                Success(defaultSettings[identity.userId]!!)

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Success(Unit)

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
                ),
            )
        }

        val repositoryWithError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithError,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            language = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithError.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        assertIs<SyncLocalToRemoteRepositoryError.SettingsNotSynced>(result.error)
    }
}
