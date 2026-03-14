package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Server-side password validation errors returned by auth operations.
 *
 * Used as a detail type within repository error variants (e.g., [SignupRepositoryError.InvalidPassword])
 * to describe why the password was rejected.
 * These are display-only — use cases branch on the parent repository error variant,
 * not on the sub-type.
 */
sealed interface PasswordValidationError {
    data class PasswordTooShort(val minLength: Int) : PasswordValidationError
    data class PasswordTooLong(val maxLength: Int) : PasswordValidationError
    data class ForbiddenCharacterInPassword(val character: Char) : PasswordValidationError
    data class PasswordMustContainNumbers(val minNumbers: Int) : PasswordValidationError
    data class PasswordMustContainAlphabet(val minAlphabet: Int) : PasswordValidationError
    data class UnknownRuleViolation(val reason: String) : PasswordValidationError
}
