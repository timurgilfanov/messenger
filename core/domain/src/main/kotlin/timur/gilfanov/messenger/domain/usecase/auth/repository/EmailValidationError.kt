package timur.gilfanov.messenger.domain.usecase.auth.repository

/**
 * Server-side email validation errors returned by auth operations.
 *
 * Used as a detail type within repository error variants (e.g., [SignupRepositoryError.InvalidEmail],
 * [LoginRepositoryError.InvalidEmail]) to describe why the email was rejected.
 * These are display-only — use cases branch on the parent repository error variant,
 * not on the sub-type.
 */
sealed interface EmailValidationError {
    data object EmailTaken : EmailValidationError
    data object EmailNotExists : EmailValidationError
    data class UnknownRuleViolation(val reason: String) : EmailValidationError
}
