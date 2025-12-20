package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId

/**
 * Retrieves user profile information.
 *
 * Fetches profile data for the specified user, including their display name
 * and profile picture URL. This operation is read-only and does not modify
 * any data.
 *
 * @param userId The unique identifier of the user whose profile to retrieve
 * @return Success with [Profile] or failure with [GetProfileError]
 *
 * ## Error Handling
 * Inherits all common errors from [UserOperationError] such as network issues,
 * authentication failures, and rate limiting.
 */
class GetProfileUseCase {
    operator fun invoke(userId: UserId): ResultWithError<Profile, GetProfileError> {
        TODO("Not implemented yet")
    }
}
