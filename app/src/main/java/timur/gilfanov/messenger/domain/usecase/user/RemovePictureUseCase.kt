package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.PictureUri
import timur.gilfanov.messenger.domain.entity.user.UserId

class RemovePictureUseCase {
    operator fun invoke(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, RemovePictureError> {
        TODO("Not implemented yet")
    }
}
