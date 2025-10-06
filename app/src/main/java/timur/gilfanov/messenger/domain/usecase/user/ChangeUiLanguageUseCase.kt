package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Changes user's UI language preference.
 *
 * Updates the user's preferred language for the application interface. The setting
 * is synchronized across all user's devices. If synchronization partially fails,
 * [ChangeUiLanguageError.LanguageNotChangedForAllDevices] is returned.
 *
 * @param userId The unique identifier of the user changing their language
 * @param newUiLanguage The new language preference to set
 * @return Success or failure with [ChangeUiLanguageError]
 *
 * ## Error Handling
 * Returns [ChangeUiLanguageError.LanguageNotChangedForAllDevices] if synchronization
 * fails for some devices, and inherits common errors from [UserOperationError].
 */
class ChangeUiLanguageUseCase {
    operator fun invoke(
        userId: UserId,
        newUiLanguage: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageError> {
        TODO("Not implemented yet")
    }
}
