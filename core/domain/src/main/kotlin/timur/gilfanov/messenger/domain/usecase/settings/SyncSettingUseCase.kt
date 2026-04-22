package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey

/**
 * Synchronizes a single user setting with the remote backend.
 */
fun interface SyncSettingUseCase {
    suspend operator fun invoke(key: SettingKey): ResultWithError<Unit, SyncSettingError>
}
