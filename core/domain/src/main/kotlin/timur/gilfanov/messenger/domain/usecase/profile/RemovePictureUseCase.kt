package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.PictureUri
import timur.gilfanov.messenger.domain.entity.profile.UserId

/**
 * Removes user's profile picture.
 *
 * Deletes the one of the user's profile picture and resets it to the default state if no one
 * picture left.
 *
 * @param userId The unique identifier of the user removing their picture
 * @param pictureUri URI of the profile picture to remove
 * @return Success or failure with [RemovePictureError]
 *
 * ## Error Handling
 * Returns [RemovePictureError.PictureNotFound] if the picture doesn't exist or
 * was already removed, and inherits common errors from [UserOperationError].
 */
class RemovePictureUseCase {
    operator fun invoke(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, RemovePictureError> {
        TODO("Not implemented yet")
    }
}
