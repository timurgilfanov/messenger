package timur.gilfanov.messenger.data.source.remote

sealed interface RemoteDataSourceErrorV2 {
    sealed interface ServiceUnavailable : RemoteDataSourceErrorV2 {
        data object NetworkNotAvailable : ServiceUnavailable
        data object ServerUnreachable : ServiceUnavailable
        data object Timeout : ServiceUnavailable
    }
    data object ServerError : RemoteDataSourceErrorV2
    data object Unauthorized : RemoteDataSourceErrorV2
    data object Forbidden : RemoteDataSourceErrorV2
    data object RateLimitExceeded : RemoteDataSourceErrorV2
}
