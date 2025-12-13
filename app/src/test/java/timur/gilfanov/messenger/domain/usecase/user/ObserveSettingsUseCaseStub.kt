package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings

class ObserveSettingsUseCaseStub(
    private val flow: Flow<ResultWithError<Settings, ObserveSettingsError>>,
) : ObserveSettingsUseCase {
    override fun invoke(): Flow<ResultWithError<Settings, ObserveSettingsError>> = flow
}
