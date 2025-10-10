package timur.gilfanov.messenger.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.LocalUserDataSourceError
import timur.gilfanov.messenger.data.source.local.UpdateSettingsLocalDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.toSettingsChangeBackupError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.foldError
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.UserRepositoryError
import timur.gilfanov.messenger.util.Logger

class SettingsRepositoryImpl(
    private val localDataSource: LocalSettingsDataSource,
    private val remoteDataSource: RemoteSettingsDataSource,
    private val logger: Logger,
) : SettingsRepository {

    companion object {
        private const val TAG = "SettingsRepository"
    }

    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, UserRepositoryError>> =
        localDataSource.observeSettings(identity.userId)
            .map { result ->
                result.foldError { error ->
                    Failure(
                        when (error) {
                            LocalUserDataSourceError.UserDataNotFound -> TODO(
                                "Create data store and restore from remote",
                            )

                            is LocalUserDataSourceError.LocalDataSource -> TODO(
                                "Looks like LocalDataSourceV2 and RepositoryError need " +
                                    "some changes, because it's hard to understand how to " +
                                    "handle local errors and also reduce them to one " +
                                    "corrupted data repository error. Return to this and " +
                                    "unhappy path implementation when local data source will" +
                                    "be implemented.",
                            )
                        },
                    )
                }
            }.distinctUntilChanged()

    override suspend fun changeLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> =
        localDataSource.updateSettings(identity.userId) { settings ->
            settings.copy(language = language)
        }.fold(
            onSuccess = {
                remoteDataSource.changeUiLanguage(identity, language).bimap(
                    onSuccess = {
                        logger.d(TAG, "Language change backed up successfully")
                    },
                    onFailure = { remoteError ->
                        ChangeLanguageRepositoryError.Backup(
                            remoteError.toSettingsChangeBackupError(),
                        )
                    },
                )
            },
            onFailure = { localError ->
                when (localError) {
                    is UpdateSettingsLocalDataSourceError.TransformError -> Failure(
                        ChangeLanguageRepositoryError.LanguageNotChanged(transient = false),
                    )

                    is UpdateSettingsLocalDataSourceError.LocalUserDataSource,
                    -> when (localError.error) {
                        LocalUserDataSourceError.UserDataNotFound -> {
                            remoteDataSource.getSettings(identity).foldWithErrorMapping(
                                onSuccess = { remoteSettings ->
                                    localDataSource.insertSettings(
                                        identity.userId,
                                        remoteSettings,
                                    ).foldWithErrorMapping(
                                        onSuccess = {
                                            changeLanguage(identity, language)
                                        },
                                        onFailure = {
                                            ChangeLanguageRepositoryError.SettingsEmpty
                                        },
                                    )
                                },
                                onFailure = {
                                    localDataSource.resetSettings(identity.userId).fold(
                                        onSuccess = {
                                            ChangeLanguageRepositoryError.SettingsResetToDefaults
                                        },
                                        onFailure = { error ->
                                            ChangeLanguageRepositoryError.SettingsEmpty
                                        },
                                    )
                                },
                            )
                        }

                        is LocalUserDataSourceError.LocalDataSource -> Failure(
                            ChangeLanguageRepositoryError.LanguageNotChanged(transient = true),
                        )
                    }
                }
            },
        )
}
