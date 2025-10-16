package timur.gilfanov.messenger.data.source.remote

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
    when (this) {
        RemoteUserDataSourceError.Authentication.SessionRevoked,
        RemoteUserDataSourceError.Authentication.TokenInvalid,
        RemoteUserDataSourceError.Authentication.TokenMissing,
        RemoteUserDataSourceError.Authentication.TokenExpired,
        RemoteUserDataSourceError.UserNotFound,
        -> SettingsChangeBackupError.Unauthenticated

        RemoteUserDataSourceError.InsufficientPermissions ->
            SettingsChangeBackupError.InsufficientPermissions

        is RemoteUserDataSourceError.RemoteDataSource ->
            when (error) {
                is RemoteDataSourceErrorV2.CooldownActive ->
                    SettingsChangeBackupError.ChangeNotBackedUp.Cooldown(error.remaining)

                RemoteDataSourceErrorV2.RateLimitExceeded,
                RemoteDataSourceErrorV2.ServerError,
                RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
                ->
                    SettingsChangeBackupError.ChangeNotBackedUp.ServiceDown

                RemoteDataSourceErrorV2.ServiceUnavailable.Timeout ->
                    SettingsChangeBackupError.ChangeBackupTimeout

                RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable ->
                    SettingsChangeBackupError.ChangeNotBackedUp.NetworkNotAvailable

                is RemoteDataSourceErrorV2.UnknownServiceError ->
                    SettingsChangeBackupError.ChangeNotBackedUp.UnknownError
            }
    }

fun RemoteUserDataSourceError.toSyncLocalToRemoteError(): SyncLocalToRemoteRepositoryError =
    when (this) {
        RemoteUserDataSourceError.Authentication.SessionRevoked,
        RemoteUserDataSourceError.Authentication.TokenExpired,
        RemoteUserDataSourceError.Authentication.TokenInvalid,
        RemoteUserDataSourceError.Authentication.TokenMissing,
        RemoteUserDataSourceError.UserNotFound,
        -> SyncLocalToRemoteRepositoryError.Unauthenticated

        RemoteUserDataSourceError.InsufficientPermissions ->
            SyncLocalToRemoteRepositoryError.InsufficientPermissions

        is RemoteUserDataSourceError.RemoteDataSource -> when (error) {
            is RemoteDataSourceErrorV2.CooldownActive ->
                SyncLocalToRemoteRepositoryError.Failed.Cooldown(error.remaining)

            RemoteDataSourceErrorV2.RateLimitExceeded,
            RemoteDataSourceErrorV2.ServerError,
            RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
            -> SyncLocalToRemoteRepositoryError.Failed.ServiceDown

            RemoteDataSourceErrorV2.ServiceUnavailable.Timeout ->
                SyncLocalToRemoteRepositoryError.StatusUnknown.ServiceTimeout

            RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable ->
                SyncLocalToRemoteRepositoryError.Failed.NetworkNotAvailable

            is RemoteDataSourceErrorV2.UnknownServiceError ->
                SyncLocalToRemoteRepositoryError.Failed.UnknownError
        }
    }
