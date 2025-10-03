package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface UserRepositoryError : RepositoryError {
    data object UserNotFound : UserRepositoryError
}
