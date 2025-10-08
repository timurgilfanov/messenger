package timur.gilfanov.messenger.domain.entity

sealed class ResultWithError<R, out E> {
    data class Success<R, E>(val data: R) : ResultWithError<R, E>()
    data class Failure<R, out E>(val error: E) : ResultWithError<R, E>()
}

val <R, E> ResultWithError<R, E>.isSuccess: Boolean get() = this is ResultWithError.Success
val <R, E> ResultWithError<R, E>.isFailure: Boolean get() = this is ResultWithError.Failure

inline fun <R, E> ResultWithError<R, E>.onSuccess(action: (R) -> Unit): ResultWithError<R, E> {
    if (this is ResultWithError.Success) {
        action(data)
    }
    return this
}

inline fun <R, E> ResultWithError<R, E>.onFailure(action: (E) -> Unit): ResultWithError<R, E> {
    if (this is ResultWithError.Failure) {
        action(error)
    }
    return this
}

// This map function is good for chained call, because it transform the result value
inline fun <R1, E1, R2, E2> ResultWithError<R1, E1>.mapResult(
    success: (R1) -> ResultWithError<R2, E2>,
    failure: (E1) -> ResultWithError<R2, E2>,
): ResultWithError<R2, E2> = when (this) {
    is ResultWithError.Failure -> failure(this.error)
    is ResultWithError.Success -> success(this.data)
}

inline fun <R, E1, E2> ResultWithError<R, E1>.mapFailure(
    failure: (E1) -> ResultWithError<R, E2>,
): ResultWithError<R, E2> = when (this) {
    is ResultWithError.Failure -> failure(this.error)
    is ResultWithError.Success -> ResultWithError.Success(this.data)
}
