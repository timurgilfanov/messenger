package timur.gilfanov.messenger.auth.domain.usecase

/**
 * Merged email validation errors combining local validator errors and server-side rejections.
 *
 * Local cases originate from [timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError.Email].
 * Remote cases ([EmailTaken], [EmailNotExists], [UnknownRuleViolation]) originate from
 * [timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError].
 */
sealed interface EmailValidationUseCaseError {
    data object BlankEmail : EmailValidationUseCaseError
    data object InvalidEmailFormat : EmailValidationUseCaseError
    data object NoAtInEmail : EmailValidationUseCaseError
    data class EmailTooLong(val maxLength: Int) : EmailValidationUseCaseError
    data object NoDomainAtEmail : EmailValidationUseCaseError
    data class ForbiddenCharacterInEmail(val character: Char) : EmailValidationUseCaseError
    data object EmailTaken : EmailValidationUseCaseError
    data object EmailNotExists : EmailValidationUseCaseError
    data class UnknownRuleViolation(val reason: String) : EmailValidationUseCaseError
}
