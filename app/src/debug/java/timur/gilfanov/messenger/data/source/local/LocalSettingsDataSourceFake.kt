package timur.gilfanov.messenger.data.source.local

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceFake(private val initialSettings: PersistentMap<UserId, Settings>) :
    LocalSettingsDataSource {

    private val settings = MutableStateFlow(initialSettings)

    override fun observeSettings(
        userId: UserId,
    ): Flow<ResultWithError<Settings, LocalUserDataSourceError>> = settings.map {
        val settings = it[userId]
        if (settings == null) {
            ResultWithError.Failure(LocalUserDataSourceError.UserDataNotFound)
        } else {
            ResultWithError.Success(settings)
        }
    }

    override suspend fun updateSettings(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> {
        settings.update {
            val userSettings = it[userId] ?: return ResultWithError.Failure(
                UpdateSettingsLocalDataSourceError.LocalUserDataSource(
                    LocalUserDataSourceError.UserDataNotFound,
                ),
            )
            it.put(userId, transform(userSettings))
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun insertSettings(
        userId: UserId,
        settings: Settings,
    ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> {
        this.settings.update {
            it.put(userId, settings)
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun resetSettings(
        userId: UserId,
    ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> =
        insertSettings(userId, initialSettings[userId]!!)
}
