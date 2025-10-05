package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UserId

interface LocalSettingsDataSource {
    fun observeSettings(userId: UserId): Flow<ResultWithError<Settings, LocalUserDataSourceError>>

    suspend fun updateSettings(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, LocalUserDataSourceError>
}
