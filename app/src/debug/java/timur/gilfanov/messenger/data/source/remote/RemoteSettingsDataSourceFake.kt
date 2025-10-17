package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

const val TIME_STEP_SECONDS = 1L

/**
 * A fake implementation of [RemoteSettingsDataSource] for testing purposes.
 *
 * While we not implement product [RemoteSettingsDataSource] we can use this fake with
 * [useRealTime] = true with production source set.
 *
 * @param initialSettings The initial settings to populate the data source with.
 * @param useRealTime If true, use system time. If false simulates real-time updates by
 * incrementing timestamps on each fetch. Default is false.
 */

class RemoteSettingsDataSourceFake(
    initialSettings: PersistentMap<UserId, Settings>,
    val useRealTime: Boolean = false,
) : RemoteSettingsDataSource {

    private val settings = MutableStateFlow(initialSettings)

    private val now: Instant = Instant.fromEpochMilliseconds(0)
        get() = if (useRealTime) {
            Clock.System.now()
        } else {
            field.plus(TIME_STEP_SECONDS.seconds)
        }

    override suspend fun getSettings(
        identity: Identity,
    ): ResultWithError<Settings, RemoteUserDataSourceError> {
        val userSettings = settings.value[identity.userId]
        return if (userSettings == null) {
            ResultWithError.Failure(RemoteUserDataSourceError.UserNotFound)
        } else {
            ResultWithError.Success(
                userSettings.copy(
                    metadata = userSettings.metadata.copy(
                        lastSyncedAt = now,
                    ),
                ),
            )
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
            it.put(
                identity.userId,
                userSettings.copy(
                    uiLanguage = language,
                    metadata = SettingsMetadata(
                        isDefault = false,
                        lastModifiedAt = now,
                        lastSyncedAt = null,
                    ),
                ),
            )
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
            it.put(
                identity.userId,
                settings.copy(
                    metadata = SettingsMetadata(
                        isDefault = settings.metadata.isDefault,
                        lastModifiedAt = now,
                        lastSyncedAt = null,
                    ),
                ),
            )
        }
        return ResultWithError.Success(Unit)
    }
}
