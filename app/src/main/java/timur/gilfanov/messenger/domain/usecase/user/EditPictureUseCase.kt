package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.PictureUri
import timur.gilfanov.messenger.domain.entity.user.UserId

class EditPictureUseCase {
    operator fun invoke(
        userId: UserId,
        pictureUri: PictureUri,
    ): ResultWithError<Unit, EditPictureError> {
        TODO("Not implemented yet")
    }
}
