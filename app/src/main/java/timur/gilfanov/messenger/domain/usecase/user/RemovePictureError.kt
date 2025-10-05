package timur.gilfanov.messenger.domain.usecase.user

sealed interface RemovePictureError : UserOperationError {
    data object PictureNotFound : RemovePictureError
}
