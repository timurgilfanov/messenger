package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

interface LocalSettingsDataSource {
    fun flowSettings(userId: UserId): Flow<ResultWithError<Settings, LocalSettingsDataSourceError>>

    suspend fun insertSettings(
        userId: UserId,
        settings: Settings,
    ): ResultWithError<Settings, LocalSettingsDataSourceError>

    suspend fun updateLanguage(
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, LocalSettingsDataSourceError>
}
