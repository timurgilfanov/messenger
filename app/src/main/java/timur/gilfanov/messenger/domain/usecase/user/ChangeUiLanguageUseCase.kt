package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

class ChangeUiLanguageUseCase {
    operator fun invoke(
        userId: UserId,
        newUiLanguage: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageError> {
        TODO("Not implemented yet")
    }
}
