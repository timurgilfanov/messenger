package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

interface SettingsRepository {
    fun observeSettings(userId: UserId): Flow<ResultWithError<Settings, SettingsRepositoryError>>

    suspend fun changeLanguage(
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError>
}
