package timur.gilfanov.messenger.auth.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

/**
 * Contract for client-side profile name validation.
 *
 * Validates names before they are submitted to the server, providing immediate feedback without
 * a network round-trip. Server-side validation may still reject names for additional reasons
 * not covered here.
 */
fun interface ProfileNameValidator {
    fun validate(name: String): ResultWithError<Unit, ProfileNameValidationError>
}
