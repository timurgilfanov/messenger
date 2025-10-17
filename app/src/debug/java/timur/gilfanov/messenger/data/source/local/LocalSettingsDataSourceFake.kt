package timur.gilfanov.messenger.data.source.local

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceFake(initialSettings: PersistentMap<UserId, Settings>) :
    LocalSettingsDataSource {

    companion object {
        private const val DEFAULT_LAST_MODIFICATION_TIMESTAMP = 1000L
    }

    private val defaultSettings = Settings(
        metadata = SettingsMetadata(
            isDefault = true,
            lastModifiedAt = Instant.fromEpochMilliseconds(DEFAULT_LAST_MODIFICATION_TIMESTAMP),
            lastSyncedAt = null,
        ),
        uiLanguage = UiLanguage.English,
    )

    private val settings = MutableStateFlow(initialSettings)

    override fun observe(
        userId: UserId,
    ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> = settings.map {
        val settings = it[userId]
        if (settings == null) {
            ResultWithError.Failure(GetSettingsLocalDataSourceError.SettingsNotFound)
        } else {
            ResultWithError.Success(settings)
        }
    }

    override suspend fun update(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> {
        settings.update {
            val userSettings = it[userId] ?: return ResultWithError.Failure(
                UpdateSettingsLocalDataSourceError.SettingsNotFound,
            )
            val transformedSettings = transform(userSettings)
            val newSettings = transformedSettings.copy(
                metadata = SettingsMetadata(
                    isDefault = userSettings.metadata.isDefault,
                    lastModifiedAt = Clock.System.now(),
                    lastSyncedAt = userSettings.metadata.lastSyncedAt,
                ),
            )
            it.put(userId, newSettings)
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun put(
        userId: UserId,
        settings: Settings,
    ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> {
        this.settings.update {
            it.put(userId, settings)
        }
        return ResultWithError.Success(Unit)
    }

    override suspend fun reset(
        userId: UserId,
    ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> = put(userId, defaultSettings)
}
