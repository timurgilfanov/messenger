package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings

class ObserveSettingsUseCaseStub(
    private val flow: Flow<ResultWithError<Settings, ObserveSettingsError>>,
) : ObserveSettingsUseCase {
    override fun invoke(): Flow<ResultWithError<Settings, ObserveSettingsError>> = flow
}
