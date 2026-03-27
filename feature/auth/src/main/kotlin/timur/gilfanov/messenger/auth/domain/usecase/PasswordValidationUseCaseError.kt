package timur.gilfanov.messenger.auth.domain.usecase

/**
 * Merged password validation errors combining local validator errors and server-side rejections.
 *
 * Local cases originate from [timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError.Password].
 * Remote cases ([UnknownRuleViolation]) originate from
 * [timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError].
 * [PasswordTooShort.minLength] and [PasswordTooLong.maxLength] are nullable to accommodate
 * servers that return these errors without specifying the bound.
 */
sealed interface PasswordValidationUseCaseError {
    data class PasswordTooShort(val minLength: Int?) : PasswordValidationUseCaseError
    data class PasswordTooLong(val maxLength: Int?) : PasswordValidationUseCaseError
    data class ForbiddenCharacterInPassword(val character: Char) : PasswordValidationUseCaseError
    data class PasswordMustContainNumbers(val minNumbers: Int) : PasswordValidationUseCaseError
    data class PasswordMustContainAlphabet(val minAlphabet: Int) : PasswordValidationUseCaseError
    data class UnknownRuleViolation(val reason: String) : PasswordValidationUseCaseError
}
