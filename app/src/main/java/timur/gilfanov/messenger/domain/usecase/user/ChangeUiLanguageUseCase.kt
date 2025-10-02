package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UiLanguage

class ChangeUiLanguageUseCase {
    operator fun invoke(newUiLanguage: UiLanguage): ResultWithError<Unit, ChangeUiLanguageError> {
        TODO("Not implemented yet")
    }
}
