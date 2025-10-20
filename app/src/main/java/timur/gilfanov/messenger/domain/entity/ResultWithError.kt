package timur.gilfanov.messenger.domain.entity

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A type representing either a successful result [Success] or a failure [Failure].
 *
 * This type is similar to Kotlin's standard [Result] but allows custom error types
 * instead of only [Throwable].
 *
 * @param R The type of the success value
 * @param E The type of the error value
 */
sealed class ResultWithError<R, out E> {
    /**
     * Represents a successful result containing a value of type [R].
     */
    data class Success<R, E>(val data: R) : ResultWithError<R, E>()

    /**
     * Represents a failed result containing an error of type [E].
     */
    data class Failure<R, out E>(val error: E) : ResultWithError<R, E>()
}

/**
 * Returns `true` if this result is [ResultWithError.Success].
 */
val <R, E> ResultWithError<R, E>.isSuccess: Boolean get() = this is ResultWithError.Success

/**
 * Returns `true` if this result is [ResultWithError.Failure].
 */
val <R, E> ResultWithError<R, E>.isFailure: Boolean get() = this is ResultWithError.Failure

/**
 * Performs the given [action] if this result is [Success], passing the success value.
 * Returns this result unchanged for chaining.
 *
 * Use this for side effects like logging without transforming the result.
 *
 * Example:
 * ```
 * getUserById(id)
 *     .onSuccess { user -> logger.log("Found user: ${user.name}") }
 *     .onFailure { error -> logger.error("Failed: $error") }
 * ```
 */
@OptIn(ExperimentalContracts::class)
inline fun <R, E> ResultWithError<R, E>.onSuccess(action: (R) -> Unit): ResultWithError<R, E> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    if (this is ResultWithError.Success) {
        action(data)
    }
    return this
}

/**
 * Performs the given [action] if this result is [Failure], passing the error value.
 * Returns this result unchanged for chaining.
 *
 * Use this for side effects like logging without transforming the result.
 */
@OptIn(ExperimentalContracts::class)
inline fun <R, E> ResultWithError<R, E>.onFailure(action: (E) -> Unit): ResultWithError<R, E> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    if (this is ResultWithError.Failure) {
        action(error)
    }
    return this
}

/**
 * Exhaustively handles both success and failure cases, reducing to a single value of type [R2].
 *
 * This is the terminal operation that extracts a value from the result by providing
 * handlers for both cases. Both handlers must return the same type [R2].
 *
 * Use this when you need to convert a [ResultWithError] to a plain value.
 *
 * Example:
 * ```
 * val message: String = getUserById(id).fold(
 *     onSuccess = { user -> "Welcome, ${user.name}" },
 *     onFailure = { error -> "Error: ${error.message}" }
 * )
 * ```
 *
 * @param onSuccess Called with the success value if this is [Success]
 * @param onFailure Called with the error value if this is [Failure]
 * @return The value returned by whichever handler was called
 */
@OptIn(ExperimentalContracts::class)
inline fun <R1, E1, R2> ResultWithError<R1, E1>.fold(
    onSuccess: (R1) -> R2,
    onFailure: (E1) -> R2,
): R2 {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is ResultWithError.Success -> onSuccess(this.data)
        is ResultWithError.Failure -> onFailure(this.error)
    }
}

/**
 * Transforms both the success value and error value, returning a new [ResultWithError].
 *
 * Maps [Success]<[R1], [E1]> to [Success]<[R2], [E2]> and
 * [Failure]<[R1], [E1]> to [Failure]<[R2], [E2]>.
 *
 * Use this when you need to transform both types between layers.
 *
 * Example:
 * ```
 * val repositoryResult: ResultWithError<User, RepositoryError> = dataSourceResult.bimap(
 *     onSuccess = { userEntity -> userEntity.toDomain() },
 *     onFailure = { dataSourceError -> dataSourceError.toRepositoryError() }
 * )
 * ```
 *
 * @param onSuccess Transforms the success value from [R1] to [R2]
 * @param onFailure Transforms the error value from [E1] to [E2]
 * @return A new result with transformed success and error types
 */
@OptIn(ExperimentalContracts::class)
inline fun <R1, E1, R2, E2> ResultWithError<R1, E1>.bimap(
    onSuccess: (R1) -> R2,
    onFailure: (E1) -> E2,
): ResultWithError<R2, E2> {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is ResultWithError.Success -> ResultWithError.Success(onSuccess(this.data))
        is ResultWithError.Failure -> ResultWithError.Failure(onFailure(this.error))
    }
}

/**
 * Chains operations on success while transforming error types on failure.
 *
 * On success, delegates to [onSuccess] which provides a new [ResultWithError].
 * On failure, transforms the error using [onFailure] and wraps it in a new [Failure].
 *
 * Use this for chaining operations that return [ResultWithError], where you also need
 * to adapt the error type between layers.
 *
 * Example:
 * ```
 * localDataSource.getUser(id)
 *     .foldWithErrorMapping(
 *         onSuccess = { user -> validateUser(user) },  // returns ResultWithError
 *         onFailure = { localError -> localError.toRepositoryError() }
 *     )
 * ```
 *
 * @param onSuccess Called with the success value, must return a new [ResultWithError]
 * @param onFailure Transforms the error value from [E1] to [E2]
 * @return The result from [onSuccess] if this is [Success], or a new [Failure] with transformed error
 */
@OptIn(ExperimentalContracts::class)
inline fun <R1, E1, R2, E2> ResultWithError<R1, E1>.foldWithErrorMapping(
    onSuccess: (R1) -> ResultWithError<R2, E2>,
    onFailure: (E1) -> E2,
): ResultWithError<R2, E2> {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is ResultWithError.Success -> onSuccess(this.data)
        is ResultWithError.Failure -> ResultWithError.Failure(onFailure(this.error))
    }
}

/**
 * Handles failures by providing an alternative [ResultWithError].
 *
 * If this is [Success], returns it unchanged. If this is [Failure], calls [action]
 * with the error to potentially recover or transform to a different error type.
 *
 * Use this for error recovery scenarios or error type transformations.
 *
 * Example:
 * ```
 * remoteDataSource.getData()
 *     .foldError { error ->
 *         when (error) {
 *             is NetworkError.NotFound -> localCache.getData()
 *             else -> ResultWithError.Failure(error.toDomainError())
 *         }
 *     }
 * ```
 *
 * @param action Called with the error value if this is [Failure]
 * @return This result if [Success], or the result from [action] if [Failure]
 */
@OptIn(ExperimentalContracts::class)
inline fun <R, E1, E2> ResultWithError<R, E1>.foldError(
    action: (E1) -> ResultWithError<R, E2>,
): ResultWithError<R, E2> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is ResultWithError.Success -> ResultWithError.Success(this.data)
        is ResultWithError.Failure -> action(this.error)
    }
}

/**
 * Transforms the error value while preserving the success value.
 *
 * Maps [Failure]<[R], [E1]> to [Failure]<[R], [E2]>, leaves [Success] unchanged.
 *
 * Use this when you need to adapt error types between layers (e.g., data layer to domain layer).
 *
 * Example:
 * ```
 * val domainResult: ResultWithError<User, DomainError> = dataResult.mapError { dataError ->
 *     when (dataError) {
 *         DataError.NotFound -> DomainError.UserNotFound
 *         DataError.NetworkIssue -> DomainError.ServiceUnavailable
 *     }
 * }
 * ```
 *
 * @param action Transforms the error value from [E1] to [E2]
 * @return A new result with the same success value but potentially different error type
 */
@OptIn(ExperimentalContracts::class)
inline fun <R, E1, E2> ResultWithError<R, E1>.mapError(
    action: (E1) -> E2,
): ResultWithError<R, E2> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is ResultWithError.Success -> ResultWithError.Success(this.data)
        is ResultWithError.Failure -> ResultWithError.Failure(action(this.error))
    }
}
