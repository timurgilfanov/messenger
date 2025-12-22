package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.PictureUri
import timur.gilfanov.messenger.domain.entity.profile.UserId

/**
 * Updates user's profile picture.
 *
 * Uploads and sets a new profile picture for the user. The picture is validated
 * against platform requirements (file size, dimensions, content policy) before upload.
 *
 * @param userId The unique identifier of the user updating their picture
 * @param pictureUri URI pointing to the new profile picture
 * @return Success or failure with [UpdatePictureError]
 *
 * ## Error Handling
 * Returns validation errors ([UpdatePictureError.FileSizeOutOfBounds],
 * [UpdatePictureError.WidthOutOfBounds], [UpdatePictureError.HeightOutOfBounds],
 * [UpdatePictureError.PlatformPolicyViolation]) and inherits common errors
 * from [UserOperationError].
 */
class UpdatePictureUseCase {
    operator fun invoke(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, UpdatePictureError> {
        TODO("Not implemented yet")
    }
}
