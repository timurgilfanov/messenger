package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface RemovePictureRepositoryError : UserRepositoryError {
    data object PictureNotFound : RemovePictureRepositoryError
}
