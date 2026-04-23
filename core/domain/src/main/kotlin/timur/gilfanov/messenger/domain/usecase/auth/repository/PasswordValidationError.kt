package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Password validation errors used in auth operations.
 *
 * Covers both server-side rejections (e.g., [SignupRepositoryError.InvalidPassword])
 * and client-side cross-field rules (e.g., [PasswordEqualToEmail]).
 * These are display-only — callers branch on the parent error variant,
 * not on the sub-type.
 */
sealed interface PasswordValidationError {
    data class PasswordTooShort(val minLength: Int?) : PasswordValidationError
    data class PasswordTooLong(val maxLength: Int?) : PasswordValidationError
    data class ForbiddenCharacterInPassword(val character: Char) : PasswordValidationError
    data class PasswordMustContainNumbers(val minNumbers: Int) : PasswordValidationError
    data class PasswordMustContainAlphabet(val minAlphabet: Int) : PasswordValidationError
    data class UnknownRuleViolation(val reason: String) : PasswordValidationError
    data object PasswordEqualToEmail : PasswordValidationError
}
