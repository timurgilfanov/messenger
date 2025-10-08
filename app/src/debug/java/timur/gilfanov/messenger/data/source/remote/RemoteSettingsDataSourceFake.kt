package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

class RemoteSettingsDataSourceFake(initialSettings: PersistentMap<UserId, Settings>) :
    RemoteSettingsDataSource {

    private val settings = MutableStateFlow(initialSettings)

    override suspend fun getSettings(
        userId: UserId,
    ): ResultWithError<Settings, RemoteUserDataSourceError> {
        val userSettings = settings.value[userId]
        return if (userSettings == null) {
            ResultWithError.Failure(RemoteUserDataSourceError.UserNotFound)
        } else {
            ResultWithError.Success(userSettings)
        }
    }

    override suspend fun changeUiLanguage(
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> {
        settings.update {
            val userSettings = it[userId] ?: return ResultWithError.Failure(
                ChangeUiLanguageRemoteDataSourceError.RemoteUserDataSource(
                    RemoteUserDataSourceError.UserNotFound,
                ),
            )
            it.put(userId, userSettings.copy(language = language))
        }
        return ResultWithError.Success(Unit)
    }
}
