package timur.gilfanov.messenger.auth.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import timur.gilfanov.messenger.auth.data.source.remote.dto.AuthTokensDto
import timur.gilfanov.messenger.auth.data.source.remote.dto.CredentialsLoginRequestDto
import timur.gilfanov.messenger.auth.data.source.remote.dto.GoogleLoginRequestDto
import timur.gilfanov.messenger.auth.data.source.remote.dto.GoogleSignupRequestDto
import timur.gilfanov.messenger.auth.data.source.remote.dto.RefreshRequestDto
import timur.gilfanov.messenger.auth.data.source.remote.dto.RegisterRequestDto
import timur.gilfanov.messenger.auth.di.UnauthenticatedHttpClient
import timur.gilfanov.messenger.data.remote.ApiError
import timur.gilfanov.messenger.data.remote.ApiResponse
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.remote.executeRemoteRequest
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.util.Logger

@Suppress("TooManyFunctions")
@Singleton
class RemoteAuthDataSourceImpl @Inject constructor(
    @UnauthenticatedHttpClient private val httpClient: HttpClient,
    private val logger: Logger,
) : RemoteAuthDataSource {

    companion object {
        private const val TAG = "RemoteAuthDataSource"
        private const val CODE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
        private const val CODE_EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"
        private const val CODE_ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"
        private const val CODE_INVALID_TOKEN = "INVALID_TOKEN"
        private const val CODE_ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND"
        private const val CODE_ACCOUNT_ALREADY_EXISTS = "ACCOUNT_ALREADY_EXISTS"
        private const val CODE_EMAIL_TAKEN = "EMAIL_TAKEN"
        private const val CODE_EMAIL_NOT_EXISTS = "EMAIL_NOT_EXISTS"
        private const val CODE_PASSWORD_TOO_SHORT = "PASSWORD_TOO_SHORT"
        private const val CODE_PASSWORD_TOO_LONG = "PASSWORD_TOO_LONG"
        private const val CODE_INVALID_NAME = "INVALID_NAME"
        private const val CODE_TOKEN_EXPIRED = "TOKEN_EXPIRED"
        private const val CODE_SESSION_REVOKED = "SESSION_REVOKED"
        private const val CODE_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
        private const val CODE_COOLDOWN_ACTIVE = "COOLDOWN_ACTIVE"
        private const val CODE_SERVER_ERROR = "SERVER_ERROR"
        private const val COOLDOWN_REMAINING_MS_KEY = "remaining_ms"
    }

    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthTokens, LoginWithCredentialsError> = executeRemoteRequest(
        TAG,
        "loginWithCredentials",
        logger,
        LoginWithCredentialsError::RemoteDataSource,
    ) {
        val response: ApiResponse<AuthTokensDto> = httpClient.post(AuthApiRoutes.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(
                CredentialsLoginRequestDto(credentials.email.value, credentials.password.value),
            )
        }.body()
        val data = response.data
        if (response.success && data != null) {
            ResultWithError.Success(data.toDomain())
        } else {
            ResultWithError.Failure(mapLoginWithCredentialsError(response.error))
        }
    }

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthTokens, LoginWithGoogleError> = executeRemoteRequest(
        TAG,
        "loginWithGoogle",
        logger,
        LoginWithGoogleError::RemoteDataSource,
    ) {
        val response: ApiResponse<AuthTokensDto> = httpClient.post(AuthApiRoutes.GOOGLE_LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(GoogleLoginRequestDto(idToken.value))
        }.body()
        val data = response.data
        if (response.success && data != null) {
            ResultWithError.Success(data.toDomain())
        } else {
            ResultWithError.Failure(mapLoginWithGoogleError(response.error))
        }
    }

    override suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthTokens, SignupWithGoogleError> = executeRemoteRequest(
        TAG,
        "signupWithGoogle",
        logger,
        SignupWithGoogleError::RemoteDataSource,
    ) {
        val response: ApiResponse<AuthTokensDto> =
            httpClient.post(AuthApiRoutes.GOOGLE_REGISTER) {
                contentType(ContentType.Application.Json)
                setBody(GoogleSignupRequestDto(idToken.value, name))
            }.body()
        val data = response.data
        if (response.success && data != null) {
            ResultWithError.Success(data.toDomain())
        } else {
            ResultWithError.Failure(mapSignupWithGoogleError(response.error))
        }
    }

    override suspend fun register(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthTokens, RegisterError> =
        executeRemoteRequest(TAG, "register", logger, RegisterError::RemoteDataSource) {
            val response: ApiResponse<AuthTokensDto> = httpClient.post(AuthApiRoutes.REGISTER) {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequestDto(credentials.email.value, credentials.password.value, name),
                )
            }.body()
            val data = response.data
            if (response.success && data != null) {
                ResultWithError.Success(data.toDomain())
            } else {
                ResultWithError.Failure(mapRegisterError(response.error))
            }
        }

    override suspend fun refresh(refreshToken: String): ResultWithError<AuthTokens, RefreshError> =
        executeRemoteRequest(TAG, "refresh", logger, RefreshError::RemoteDataSource) {
            val response: ApiResponse<AuthTokensDto> = httpClient.post(AuthApiRoutes.REFRESH) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequestDto(refreshToken))
            }.body()
            val data = response.data
            if (response.success && data != null) {
                ResultWithError.Success(data.toDomain())
            } else {
                ResultWithError.Failure(mapRefreshError(response.error))
            }
        }

    override suspend fun logout(accessToken: String): ResultWithError<Unit, LogoutError> =
        executeRemoteRequest(TAG, "logout", logger, LogoutError::RemoteDataSource) {
            val response: ApiResponse<Unit> = httpClient.post(AuthApiRoutes.LOGOUT) {
                contentType(ContentType.Application.Json)
                bearerAuth(accessToken)
            }.body()
            if (response.success) {
                ResultWithError.Success(Unit)
            } else {
                ResultWithError.Failure(mapLogoutError(response.error))
            }
        }

    private fun infraError(apiError: ApiError?): RemoteDataSourceError {
        val code = apiError?.code ?: return RemoteDataSourceError.ServerError
        return when (code) {
            CODE_RATE_LIMIT_EXCEEDED -> RemoteDataSourceError.RateLimitExceeded
            CODE_COOLDOWN_ACTIVE -> {
                val remainingMs = apiError.details?.get(COOLDOWN_REMAINING_MS_KEY)?.toLongOrNull()
                if (remainingMs != null) {
                    RemoteDataSourceError.CooldownActive(remainingMs.milliseconds)
                } else {
                    RemoteDataSourceError.UnknownServiceError(
                        ErrorReason("COOLDOWN_ACTIVE missing remaining_ms"),
                    )
                }
            }
            CODE_SERVER_ERROR -> RemoteDataSourceError.ServerError
            else -> RemoteDataSourceError.UnknownServiceError(ErrorReason(apiError.message))
        }
    }

    private fun mapLoginWithCredentialsError(apiError: ApiError?): LoginWithCredentialsError =
        when (apiError?.code) {
            CODE_INVALID_CREDENTIALS -> LoginWithCredentialsError.InvalidCredentials
            CODE_EMAIL_NOT_VERIFIED -> LoginWithCredentialsError.EmailNotVerified
            CODE_ACCOUNT_SUSPENDED -> LoginWithCredentialsError.AccountSuspended
            else -> LoginWithCredentialsError.RemoteDataSource(infraError(apiError))
        }

    private fun mapSignupWithGoogleError(apiError: ApiError?): SignupWithGoogleError =
        when (apiError?.code) {
            CODE_INVALID_TOKEN -> SignupWithGoogleError.InvalidToken
            CODE_ACCOUNT_ALREADY_EXISTS -> SignupWithGoogleError.AccountAlreadyExists
            CODE_INVALID_NAME -> SignupWithGoogleError.InvalidName(
                ProfileNameValidationError.UnknownRuleViolation(apiError.message),
            )
            else -> SignupWithGoogleError.RemoteDataSource(infraError(apiError))
        }

    private fun mapLoginWithGoogleError(apiError: ApiError?): LoginWithGoogleError =
        when (apiError?.code) {
            CODE_INVALID_TOKEN -> LoginWithGoogleError.InvalidToken
            CODE_ACCOUNT_NOT_FOUND -> LoginWithGoogleError.AccountNotFound
            CODE_ACCOUNT_SUSPENDED -> LoginWithGoogleError.AccountSuspended
            else -> LoginWithGoogleError.RemoteDataSource(infraError(apiError))
        }

    private fun mapRegisterError(apiError: ApiError?): RegisterError = when (apiError?.code) {
        CODE_EMAIL_TAKEN -> RegisterError.InvalidEmail(EmailValidationError.EmailTaken)
        CODE_EMAIL_NOT_EXISTS -> RegisterError.InvalidEmail(EmailValidationError.EmailNotExists)
        CODE_PASSWORD_TOO_SHORT -> RegisterError.InvalidPassword(
            PasswordValidationError.PasswordTooShort(
                apiError.details?.get("min_length")?.toIntOrNull(),
            ),
        )
        CODE_PASSWORD_TOO_LONG -> RegisterError.InvalidPassword(
            PasswordValidationError.PasswordTooLong(
                apiError.details?.get("max_length")?.toIntOrNull(),
            ),
        )
        CODE_INVALID_NAME -> RegisterError.InvalidName(
            ProfileNameValidationError.UnknownRuleViolation(apiError.message),
        )
        else -> RegisterError.RemoteDataSource(infraError(apiError))
    }

    private fun mapRefreshError(apiError: ApiError?): RefreshError = when (apiError?.code) {
        CODE_TOKEN_EXPIRED -> RefreshError.TokenExpired
        CODE_SESSION_REVOKED -> RefreshError.SessionRevoked
        else -> RefreshError.RemoteDataSource(infraError(apiError))
    }

    private fun mapLogoutError(apiError: ApiError?): LogoutError =
        LogoutError.RemoteDataSource(infraError(apiError))
}
