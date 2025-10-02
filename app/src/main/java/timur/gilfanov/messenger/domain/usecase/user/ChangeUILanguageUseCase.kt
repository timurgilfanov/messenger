package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UILanguage

class ChangeUILanguageUseCase {
    operator fun invoke(newUILanguage: UILanguage): ResultWithError<Unit, ChangeUILanguageError> {
        TODO("Not implemented yet")
    }
}
