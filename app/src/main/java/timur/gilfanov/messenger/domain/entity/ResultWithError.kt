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
