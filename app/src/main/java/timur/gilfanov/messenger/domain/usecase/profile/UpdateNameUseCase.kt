package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.UserId

/**
 * Updates user's display name.
 *
 * Changes the user's publicly visible display name. The name is validated
 * against platform rules (length, forbidden characters, content policy) before
 * being sent to the backend.
 *
 * @param userId The unique identifier of the user updating their name
 * @param newName The new display name to set
 * @return Success or failure with [UpdateNameError]
 *
 * ## Error Handling
 * Returns validation errors ([UpdateNameError.LengthOutOfBounds],
 * [UpdateNameError.ForbiddenCharacter], [UpdateNameError.PlatformPolicyViolation])
 * and inherits common errors from [UserOperationError].
 */
class UpdateNameUseCase {
    operator fun invoke(userId: UserId, newName: String): ResultWithError<Unit, UpdateNameError> {
        TODO("Not implemented yet")
    }
}
