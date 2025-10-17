package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.data.source.ErrorReason
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
import timur.gilfanov.messenger.data.source.remote.TIME_STEP_SECONDS
import timur.gilfanov.messenger.data.source.remote.UpdateSettingsRemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.SettingsState
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
                uiLanguage = defaultUiLanguage,
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
            assertEquals(defaultUiLanguage, initialResult.data.uiLanguage)
            expectNoEvents()

            val newUiLanguage = UiLanguage.German
            val changeLanguageResult = repository.changeUiLanguage(identity, newUiLanguage)
            assertIs<Success<Unit, *>>(changeLanguageResult)

            val updatedResult = awaitItem()
            assertIs<Success<Settings, *>>(updatedResult)
            assertEquals(newUiLanguage, updatedResult.data.uiLanguage)
        }
    }

    @Test
    fun `observeSettings triggers recovery when SettingsNotFound`() = runTest {
        val emptyLocalDataSource = LocalSettingsDataSourceFake(persistentMapOf())
        val remoteSettings = Settings(
            uiLanguage = UiLanguage.German,
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
            assertEquals(UiLanguage.German, result.data.uiLanguage)
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
                        LocalDataSourceErrorV2.ReadError(ErrorReason("DataStore corruption")),
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
    fun `observeSettings recovery returns SettingsEmpty when insert fails`() = runTest {
        val localDataSourceWithInsertFailure = object : LocalSettingsDataSource {
            override fun observeSettings(
                userId: UserId,
            ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                Failure(GetSettingsLocalDataSourceError.SettingsNotFound),
            )

            override suspend fun updateSettings(
                userId: UserId,
                transform: (Settings) -> Settings,
            ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Success(Unit)

            override suspend fun insertSettings(
                userId: UserId,
                settings: Settings,
            ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> = Failure(
                LocalDataSourceErrorV2.WriteError(ErrorReason("Insert failed during recovery")),
            )

            override suspend fun resetSettings(
                userId: UserId,
            ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
        }

        val remoteDataSource = RemoteSettingsDataSourceFake(defaultSettings)

        val repositoryWithInsertFailure = SettingsRepositoryImpl(
            localDataSource = localDataSourceWithInsertFailure,
            remoteDataSource = remoteDataSource,
            logger = NoOpLogger(),
        )

        repositoryWithInsertFailure.observeSettings(identity).test {
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
            assertEquals(SettingsState.DEFAULT, secondResult.data.metadata.state)
        }
    }

    @Test
    fun `applyRemoteSettings succeeds`() = runTest {
        val newSettings = Settings(
            uiLanguage = UiLanguage.German,
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
            uiLanguage = UiLanguage.German,
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
    fun `syncLocalToRemote ignores local insert failure after remote success`() = runTest {
        val localWithInsertFailure = object : LocalSettingsDataSource {
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
                LocalDataSourceErrorV2.WriteError(ErrorReason("Local mirror insert failed")),
            )

            override suspend fun resetSettings(
                userId: UserId,
            ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
        }

        val remoteAlwaysSuccess = object : RemoteSettingsDataSource {
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
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithLocalFailure = SettingsRepositoryImpl(
            localDataSource = localWithInsertFailure,
            remoteDataSource = remoteAlwaysSuccess,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithLocalFailure.syncLocalToRemote(identity, settingsToSync)

        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `changeUiLanguage returns Backup error when remote sync fails`() = runTest {
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

        val result = repositoryWithBackupError.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        assertIs<ChangeLanguageRepositoryError.Backup>(result.error)
        assertIs<SettingsChangeBackupError.ChangeNotBackedUp.ServiceDown>(result.error.error)
    }

    @Test
    fun `changeUiLanguage returns transient LanguageNotChanged when local write fails`() = runTest {
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
                UpdateSettingsLocalDataSourceError.UpdateSettingsLocalDataSource(
                    LocalDataSourceErrorV2.WriteError(ErrorReason("Write failed")),
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

        val result = repositoryWithError.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        assertIs<ChangeLanguageRepositoryError.LanguageNotChanged>(result.error)
        assertEquals(true, result.error.transient)
    }

    @Test
    fun `changeUiLanguage returns non transient LanguageNotChanged when deserialization fails`() =
        runTest {
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
                    UpdateSettingsLocalDataSourceError.GetSettingsLocalDataSource(
                        LocalDataSourceErrorV2.DeserializationError(
                            ErrorReason("Corrupted data"),
                        ),
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

            val result = repositoryWithError.changeUiLanguage(identity, UiLanguage.German)

            assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
            assertIs<ChangeLanguageRepositoryError.LanguageNotChanged>(result.error)
            assertEquals(false, result.error.transient)
        }

    @Test
    fun `changeUiLanguage returns Backup unauthenticated when remote session revoked`() = runTest {
        val remoteWithAuthError = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> =
                Success(defaultSettings[identity.userId]!!)

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.Authentication.SessionRevoked,
            )

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithAuthError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithAuthError,
            logger = NoOpLogger(),
        )

        val result = repositoryWithAuthError.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        val error = result.error
        assertIs<ChangeLanguageRepositoryError.Backup>(error)
        assertIs<SettingsChangeBackupError.Unauthenticated>(error.error)
    }

    @Test
    fun `changeUiLanguage returns Backup insufficient permissions when remote denies`() = runTest {
        val remoteWithPermissionError = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> =
                Success(defaultSettings[identity.userId]!!)

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.InsufficientPermissions,
            )

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithPermissionError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithPermissionError,
            logger = NoOpLogger(),
        )

        val result = repositoryWithPermissionError.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        val error = result.error
        assertIs<ChangeLanguageRepositoryError.Backup>(error)
        assertIs<SettingsChangeBackupError.InsufficientPermissions>(error.error)
    }

    @Test
    fun `changeUiLanguage returns ChangeBackupTimeout when remote times out`() = runTest {
        val remoteWithTimeout = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> =
                Success(defaultSettings[identity.userId]!!)

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServiceUnavailable.Timeout,
                ),
            )

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithTimeout = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithTimeout,
            logger = NoOpLogger(),
        )

        val result = repositoryWithTimeout.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        val error = result.error
        assertIs<ChangeLanguageRepositoryError.Backup>(error)
        assertIs<SettingsChangeBackupError.ChangeBackupTimeout>(error.error)
    }

    @Test
    fun `changeUiLanguage returns Backup network not available when remote offline`() = runTest {
        val remoteWithNetworkError = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> =
                Success(defaultSettings[identity.userId]!!)

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable,
                ),
            )

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithNetworkError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithNetworkError,
            logger = NoOpLogger(),
        )

        val result = repositoryWithNetworkError.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        val error = result.error
        assertIs<ChangeLanguageRepositoryError.Backup>(error)
        assertIs<SettingsChangeBackupError.ChangeNotBackedUp.NetworkNotAvailable>(error.error)
    }

    @Test
    fun `changeUiLanguage returns Backup unknown error when remote unexpected`() = runTest {
        val remoteWithUnknownError = object : RemoteSettingsDataSource {
            override suspend fun getSettings(
                identity: Identity,
            ): ResultWithError<Settings, RemoteUserDataSourceError> =
                Success(defaultSettings[identity.userId]!!)

            override suspend fun changeUiLanguage(
                identity: Identity,
                language: UiLanguage,
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Failure(
                RemoteUserDataSourceError.RemoteDataSource(
                    RemoteDataSourceErrorV2.UnknownServiceError(ErrorReason("unexpected")),
                ),
            )

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithUnknownError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithUnknownError,
            logger = NoOpLogger(),
        )

        val result = repositoryWithUnknownError.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        val error = result.error
        assertIs<ChangeLanguageRepositoryError.Backup>(error)
        assertIs<SettingsChangeBackupError.ChangeNotBackedUp.UnknownError>(error.error)
    }

    @Test
    fun `changeUiLanguage returns transient LanguageNotChanged when read fails`() = runTest {
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
                UpdateSettingsLocalDataSourceError.GetSettingsLocalDataSource(
                    LocalDataSourceErrorV2.ReadError(ErrorReason("Read failed")),
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

        val result = repositoryWithError.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        assertIs<ChangeLanguageRepositoryError.LanguageNotChanged>(result.error)
        assertEquals(true, result.error.transient)
    }

    @Test
    fun `changeUiLanguage returns non transient LanguageNotChanged when transform fails`() =
        runTest {
            val localDataSourceWithTransformError = object : LocalSettingsDataSource {
                override fun observeSettings(
                    userId: UserId,
                ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                    Success(defaultSettings[userId]!!),
                )

                override suspend fun updateSettings(
                    userId: UserId,
                    transform: (Settings) -> Settings,
                ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Failure(
                    UpdateSettingsLocalDataSourceError.TransformError(
                        ErrorReason("Transform failed"),
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

            val repositoryWithTransformError = SettingsRepositoryImpl(
                localDataSource = localDataSourceWithTransformError,
                remoteDataSource = RemoteSettingsDataSourceFake(defaultSettings),
                logger = NoOpLogger(),
            )

            val result = repositoryWithTransformError.changeUiLanguage(identity, UiLanguage.German)

            assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
            assertIs<ChangeLanguageRepositoryError.LanguageNotChanged>(result.error)
            assertEquals(false, result.error.transient)
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
                LocalDataSourceErrorV2.WriteError(ErrorReason("Insert failed")),
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
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(200),
                lastSyncedAt = Instant.fromEpochMilliseconds(200),
            ),
        )

        val result = repositoryWithError.applyRemoteSettings(identity, newSettings)

        assertIs<Failure<*, ApplyRemoteSettingsRepositoryError>>(result)
        assertIs<ApplyRemoteSettingsRepositoryError.Transient>(result.error)
    }

    @Test
    fun `applyRemoteSettings returns non transient error when serialization fails`() = runTest {
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
                LocalDataSourceErrorV2.SerializationError(ErrorReason("Serialization failed")),
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
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(200),
                lastSyncedAt = Instant.fromEpochMilliseconds(200),
            ),
        )

        val result = repositoryWithError.applyRemoteSettings(identity, newSettings)

        assertIs<Failure<*, ApplyRemoteSettingsRepositoryError>>(result)
        assertIs<ApplyRemoteSettingsRepositoryError.NotTransient>(result.error)
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
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithError.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        assertIs<SyncLocalToRemoteRepositoryError.Failed.ServiceDown>(result.error)
    }

    @Test
    fun `syncLocalToRemote returns cooldown when remote throttles`() = runTest {
        val cooldown = 5.seconds
        val remoteWithCooldown = object : RemoteSettingsDataSource {
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
                    RemoteDataSourceErrorV2.CooldownActive(cooldown),
                ),
            )
        }

        val repositoryWithCooldown = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithCooldown,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithCooldown.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        val error = result.error
        assertIs<SyncLocalToRemoteRepositoryError.Failed.Cooldown>(error)
        assertEquals(cooldown, error.remaining)
    }

    @Test
    fun `syncLocalToRemote returns unauthenticated when remote session revoked`() = runTest {
        val remoteWithAuthError = object : RemoteSettingsDataSource {
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
                RemoteUserDataSourceError.Authentication.SessionRevoked,
            )
        }

        val repositoryWithAuthError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithAuthError,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithAuthError.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        assertIs<SyncLocalToRemoteRepositoryError.Unauthenticated>(result.error)
    }

    @Test
    fun `syncLocalToRemote returns timeout when remote status unknown`() = runTest {
        val remoteWithTimeout = object : RemoteSettingsDataSource {
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
                    RemoteDataSourceErrorV2.ServiceUnavailable.Timeout,
                ),
            )
        }

        val repositoryWithTimeout = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithTimeout,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithTimeout.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        assertIs<SyncLocalToRemoteRepositoryError.StatusUnknown.ServiceTimeout>(result.error)
    }

    @Test
    fun `syncLocalToRemote returns insufficient permissions when remote denies`() = runTest {
        val remoteWithPermissionError = object : RemoteSettingsDataSource {
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
                RemoteUserDataSourceError.InsufficientPermissions,
            )
        }

        val repositoryWithPermissionError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithPermissionError,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithPermissionError.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        assertIs<SyncLocalToRemoteRepositoryError.InsufficientPermissions>(result.error)
    }

    @Test
    fun `syncLocalToRemote returns network not available when offline`() = runTest {
        val remoteWithNetworkError = object : RemoteSettingsDataSource {
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
                    RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable,
                ),
            )
        }

        val repositoryWithNetworkError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithNetworkError,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithNetworkError.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        assertIs<SyncLocalToRemoteRepositoryError.Failed.NetworkNotAvailable>(result.error)
    }

    @Test
    fun `syncLocalToRemote returns unknown error when remote unexpected`() = runTest {
        val remoteWithUnknownError = object : RemoteSettingsDataSource {
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
                    RemoteDataSourceErrorV2.UnknownServiceError(ErrorReason("unexpected")),
                ),
            )
        }

        val repositoryWithUnknownError = SettingsRepositoryImpl(
            localDataSource = LocalSettingsDataSourceFake(defaultSettings),
            remoteDataSource = remoteWithUnknownError,
            logger = NoOpLogger(),
        )

        val settingsToSync = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(300),
                lastSyncedAt = Instant.fromEpochMilliseconds(300),
            ),
        )

        val result = repositoryWithUnknownError.syncLocalToRemote(identity, settingsToSync)

        assertIs<Failure<*, SyncLocalToRemoteRepositoryError>>(result)
        assertIs<SyncLocalToRemoteRepositoryError.Failed.UnknownError>(result.error)
    }

    @Test
    fun `changeUiLanguage triggers recovery and succeeds when remote has settings`() = runTest {
        val localDataSourceWithRecovery = object : LocalSettingsDataSource {
            private var currentSettings: Settings? = null

            override fun observeSettings(
                userId: UserId,
            ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                currentSettings?.let { Success(it) }
                    ?: Success(defaultSettings[userId]!!),
            )

            override suspend fun updateSettings(
                userId: UserId,
                transform: (Settings) -> Settings,
            ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> =
                if (currentSettings == null) {
                    Failure(UpdateSettingsLocalDataSourceError.SettingsNotFound)
                } else {
                    currentSettings = transform(currentSettings!!)
                    Success(Unit)
                }

            override suspend fun insertSettings(
                userId: UserId,
                settings: Settings,
            ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> {
                currentSettings = settings
                return Success(Unit)
            }

            override suspend fun resetSettings(
                userId: UserId,
            ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
        }

        val remoteSettings = Settings(
            uiLanguage = UiLanguage.English,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(100),
                lastSyncedAt = Instant.fromEpochMilliseconds(100),
            ),
        )
        val remoteDataSource = RemoteSettingsDataSourceFake(
            persistentMapOf(identity.userId to remoteSettings),
        )

        val repositoryWithRecovery = SettingsRepositoryImpl(
            localDataSource = localDataSourceWithRecovery,
            remoteDataSource = remoteDataSource,
            logger = NoOpLogger(),
        )

        val result = repositoryWithRecovery.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `changeUiLanguage returns SettingsEmpty when recovery fails completely`() = runTest {
        val localDataSourceWithFailures = object : LocalSettingsDataSource {
            override fun observeSettings(
                userId: UserId,
            ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                Success(defaultSettings[userId]!!),
            )

            override suspend fun updateSettings(
                userId: UserId,
                transform: (Settings) -> Settings,
            ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Failure(
                UpdateSettingsLocalDataSourceError.SettingsNotFound,
            )

            override suspend fun insertSettings(
                userId: UserId,
                settings: Settings,
            ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> = Success(Unit)

            override suspend fun resetSettings(
                userId: UserId,
            ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Failure(
                LocalDataSourceErrorV2.WriteError(ErrorReason("Reset failed")),
            )
        }

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
            ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Success(Unit)

            override suspend fun updateSettings(
                identity: Identity,
                settings: Settings,
            ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
        }

        val repositoryWithCompleteFailure = SettingsRepositoryImpl(
            localDataSource = localDataSourceWithFailures,
            remoteDataSource = remoteWithError,
            logger = NoOpLogger(),
        )

        val result = repositoryWithCompleteFailure.changeUiLanguage(identity, UiLanguage.German)

        assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
        assertIs<ChangeLanguageRepositoryError.SettingsEmpty>(result.error)
    }

    @Test
    fun `changeUiLanguage returns SettingsResetToDefaults when recovery resets settings`() =
        runTest {
            val localDataSourceWithReset = object : LocalSettingsDataSource {
                override fun observeSettings(
                    userId: UserId,
                ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                    Success(defaultSettings[userId]!!),
                )

                override suspend fun updateSettings(
                    userId: UserId,
                    transform: (Settings) -> Settings,
                ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Failure(
                    UpdateSettingsLocalDataSourceError.SettingsNotFound,
                )

                override suspend fun insertSettings(
                    userId: UserId,
                    settings: Settings,
                ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> = Success(Unit)

                override suspend fun resetSettings(
                    userId: UserId,
                ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
            }

            val remoteWithTemporaryFailure = object : RemoteSettingsDataSource {
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
                ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = Success(Unit)

                override suspend fun updateSettings(
                    identity: Identity,
                    settings: Settings,
                ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = Success(Unit)
            }

            val repositoryWithReset = SettingsRepositoryImpl(
                localDataSource = localDataSourceWithReset,
                remoteDataSource = remoteWithTemporaryFailure,
                logger = NoOpLogger(),
            )

            val result = repositoryWithReset.changeUiLanguage(identity, UiLanguage.German)

            assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
            assertIs<ChangeLanguageRepositoryError.SettingsResetToDefaults>(result.error)
        }

    @Test
    fun `changeUiLanguage returns SettingsConflict when local modified and remote newer`() =
        runTest {
            val localModifiedSettings = Settings(
                uiLanguage = UiLanguage.German,
                metadata = SettingsMetadata(
                    isDefault = false,
                    lastModifiedAt = Instant.fromEpochMilliseconds(150),
                    lastSyncedAt = Instant.fromEpochMilliseconds(100),
                ),
            )

            val remoteNewerSettings = Settings(
                uiLanguage = UiLanguage.English,
                metadata = SettingsMetadata(
                    isDefault = false,
                    lastModifiedAt = Instant.fromEpochMilliseconds(200),
                    lastSyncedAt = null,
                ),
            )

            val localDataSourceWithConflict = object : LocalSettingsDataSource {
                override fun observeSettings(
                    userId: UserId,
                ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = flowOf(
                    Success(localModifiedSettings),
                )

                override suspend fun updateSettings(
                    userId: UserId,
                    transform: (Settings) -> Settings,
                ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> = Failure(
                    UpdateSettingsLocalDataSourceError.SettingsNotFound,
                )

                override suspend fun insertSettings(
                    userId: UserId,
                    settings: Settings,
                ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> = Success(Unit)

                override suspend fun resetSettings(
                    userId: UserId,
                ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = Success(Unit)
            }

            val remoteDataSource = RemoteSettingsDataSourceFake(
                persistentMapOf(identity.userId to remoteNewerSettings),
            )

            val repositoryWithConflict = SettingsRepositoryImpl(
                localDataSource = localDataSourceWithConflict,
                remoteDataSource = remoteDataSource,
                logger = NoOpLogger(),
            )

            val result = repositoryWithConflict.changeUiLanguage(identity, UiLanguage.English)
            val remoteSettingsFetched = remoteNewerSettings.copy(
                metadata = remoteNewerSettings.metadata.copy(
                    lastSyncedAt = Instant.fromEpochSeconds(TIME_STEP_SECONDS),
                ),
            )

            assertIs<Failure<*, ChangeLanguageRepositoryError>>(result)
            assertIs<ChangeLanguageRepositoryError.SettingsConflict>(result.error)
            assertEquals(localModifiedSettings, result.error.localSettings)
            assertEquals(remoteSettingsFetched, result.error.remoteSettings)
        }
}
