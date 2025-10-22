package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsChangeBackupError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncLocalToRemoteRepositoryError

/**
 * Errors specific to remote user data operations.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - User does not exist on the backend
 *
 * ## Authentication Errors (Who are you?)
 * - [Authentication] - Identity verification failures
 *
 * ## Authorization Errors (What can you do?)
 * - [InsufficientPermissions] - Authenticated user lacks required permissions
 *
 * ## Common Errors
 * - [RemoteDataSource] - Wraps common remote data source errors
 */
sealed interface RemoteUserDataSourceError {
    /**
     * User does not exist on the backend.
     *
     * Indicates the requested user ID is not found in the system.
     */
    data object UserNotFound : RemoteUserDataSourceError

    /**
     * Authentication errors related to identity verification.
     *
     * These errors indicate problems with proving who the user is.
     * Resolution typically requires re-authentication (login).
     */
    sealed interface Authentication : RemoteUserDataSourceError {
        /**
         * Authentication token is missing from the request.
         */
        data object TokenMissing : Authentication

        /**
         * Authentication token has expired.
         *
         * User session has timed out and requires re-authentication.
         */
        data object TokenExpired : Authentication

        /**
         * Authentication token is invalid or malformed.
         *
         * May indicate token tampering or corruption.
         */
        data object TokenInvalid : Authentication

        /**
         * User session has been revoked.
         *
         * Token was valid but session was explicitly terminated
         * (e.g., user logged out from another device, password changed).
         */
        data object SessionRevoked : Authentication
    }

    /**
     * User lacks required permissions for this operation.
     *
     * User is authenticated (identity verified) but does not have
     * the necessary permissions or role to perform the requested action.
     * This is an authorization failure, not authentication.
     */
    data object InsufficientPermissions : RemoteUserDataSourceError

    /**
     * Common remote data source errors.
     *
     * @property error The underlying remote data source error
     */
    data class RemoteDataSource(val error: RemoteDataSourceErrorV2) : RemoteUserDataSourceError
}

fun RemoteUserDataSourceError.toSettingsChangeBackupError(): SettingsChangeBackupError =
    this.toRepositoryError()

fun RemoteUserDataSourceError.toSyncLocalToRemoteError(): SyncLocalToRemoteRepositoryError =
    this.toRepositoryError()

fun RemoteUserDataSourceError.toRepositoryError(): RepositoryError = when (this) {
    RemoteUserDataSourceError.Authentication.SessionRevoked,
    RemoteUserDataSourceError.Authentication.TokenInvalid,
    RemoteUserDataSourceError.Authentication.TokenMissing,
    RemoteUserDataSourceError.Authentication.TokenExpired,
    RemoteUserDataSourceError.UserNotFound,
    -> RepositoryError.Unauthenticated

    RemoteUserDataSourceError.InsufficientPermissions ->
        RepositoryError.InsufficientPermissions

    is RemoteUserDataSourceError.RemoteDataSource ->
        when (error) {
            is RemoteDataSourceErrorV2.CooldownActive ->
                RepositoryError.Failed.Cooldown(error.remaining)

            RemoteDataSourceErrorV2.RateLimitExceeded,
            RemoteDataSourceErrorV2.ServerError,
            RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
            ->
                RepositoryError.Failed.ServiceDown

            RemoteDataSourceErrorV2.ServiceUnavailable.Timeout ->
                RepositoryError.UnknownStatus.ServiceTimeout

            RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable ->
                RepositoryError.Failed.NetworkNotAvailable

            is RemoteDataSourceErrorV2.UnknownServiceError ->
                RepositoryError.Failed.UnknownServiceError
        }
}
