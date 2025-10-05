package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

interface RemoteSettingsDataSource {
    suspend fun getSettings(userId: UserId): ResultWithError<Settings, RemoteUserDataSourceError>

    suspend fun changeUiLanguage(
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError>
}
