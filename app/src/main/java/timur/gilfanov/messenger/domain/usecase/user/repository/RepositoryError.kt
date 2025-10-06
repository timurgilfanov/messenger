package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface RepositoryError {
    sealed interface ServiceUnavailable : RepositoryError {
        data object NoConnectivity : ServiceUnavailable
        data object ServiceDown : ServiceUnavailable
        data object Timeout : ServiceUnavailable
    }
    data object AccessDenied : RepositoryError
    data class UnknownServiceError(val reason: String) : RepositoryError
    data object LocalDataCorrupted : RepositoryError
}
