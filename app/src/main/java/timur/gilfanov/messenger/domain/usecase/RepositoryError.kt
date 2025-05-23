package timur.gilfanov.messenger.domain.usecase

sealed class RepositoryError {
    object NetworkError : RepositoryError()
    object ServerError : RepositoryError()
    object UnknownError : RepositoryError()
}
