package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings

fun interface ObserveSettingsUseCase {
    operator fun invoke(): Flow<ResultWithError<Settings, ObserveSettingsError>>
}
