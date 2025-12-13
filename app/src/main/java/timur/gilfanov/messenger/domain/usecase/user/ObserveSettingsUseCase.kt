package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings

fun interface ObserveSettingsUseCase {
    operator fun invoke(): Flow<ResultWithError<Settings, ObserveSettingsError>>
}
