package timur.gilfanov.messenger.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSource
import timur.gilfanov.messenger.data.source.local.LocalUserDataSourceError
import timur.gilfanov.messenger.data.source.remote.ChangeUiLanguageRemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.mapFailure
import timur.gilfanov.messenger.domain.entity.mapResult
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.UserRepositoryError

class SettingsRepositoryImpl(
    private val localDataSource: LocalSettingsDataSource,
    private val remoteDataSource: RemoteSettingsDataSource,
) : SettingsRepository {
    override fun observeSettings(
        userId: UserId,
    ): Flow<ResultWithError<Settings, UserRepositoryError>> =
        localDataSource.observeSettings(userId)
            .map { result ->
                result.mapFailure { error ->
                    Failure(
                        when (error) {
                            LocalUserDataSourceError.UserNotFound ->
                                UserRepositoryError.UserNotFound

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
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> =
        remoteDataSource.changeUiLanguage(userId, language).mapResult(
            success = {
                localDataSource.updateSettings(userId) { settings ->
                    settings.copy(language = language)
                }.mapFailure { localError ->
                    when (localError) {
                        LocalUserDataSourceError.UserNotFound -> Failure(
                            ChangeLanguageRepositoryError.UserRepository(
                                UserRepositoryError.UserNotFound,
                            ),
                        )

                        is LocalUserDataSourceError.LocalDataSource -> TODO(
                            "Return to unhappy path implementation when local data " +
                                "source will be implemented.",
                        )
                    }
                }
            },
            failure = { remoteError ->
                when (remoteError) {
                    ChangeUiLanguageRemoteDataSourceError.LanguageNotChangedForAllDevices ->
                        Failure(ChangeLanguageRepositoryError.LanguageNotChangedForAllDevices)

                    is ChangeUiLanguageRemoteDataSourceError.RemoteUserDataSource -> TODO(
                        "Work on unhappy path after remote data source implemented. " +
                            "Probably, this will require changes in RemoteDataSourceErrorV2" +
                            "and, definitely, a documentation.",
                    )
                }
            },
        )
}
