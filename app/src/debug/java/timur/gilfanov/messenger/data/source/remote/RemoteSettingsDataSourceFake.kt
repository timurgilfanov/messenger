package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

class RemoteSettingsDataSourceFake(initialSettings: PersistentMap<UserId, Settings>) :
    RemoteSettingsDataSource {

    private val settings = MutableStateFlow(initialSettings)

    override suspend fun getSettings(
        identity: Identity,
    ): ResultWithError<Settings, RemoteUserDataSourceError> {
        val userSettings = settings.value[identity.userId]
        return if (userSettings == null) {
            ResultWithError.Failure(RemoteUserDataSourceError.UserNotFound)
        } else {
            ResultWithError.Success(userSettings)
        }
    }

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> {
        settings.update {
            val userSettings = it[identity.userId] ?: return ResultWithError.Failure(
                RemoteUserDataSourceError.UserNotFound,
            )
            it.put(identity.userId, userSettings.copy(language = language))
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun updateSettings(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> {
        this.settings.update {
            if (!it.containsKey(identity.userId)) {
                return ResultWithError.Failure(RemoteUserDataSourceError.UserNotFound)
            }
            it.put(identity.userId, settings)
        }
        return ResultWithError.Success(Unit)
    }
}
