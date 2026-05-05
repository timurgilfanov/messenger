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
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailUnknownError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError
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
    ): ResultWithError<AuthTokens, LoginWithCredentialsRemoteDataSourceError> =
        executeRemoteRequest(
            TAG,
            "loginWithCredentials",
            logger,
            LoginWithCredentialsRemoteDataSourceError::RemoteDataSource,
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
    ): ResultWithError<AuthTokens, LoginWithGoogleRemoteDataSourceError> = executeRemoteRequest(
        TAG,
        "loginWithGoogle",
        logger,
        LoginWithGoogleRemoteDataSourceError::RemoteDataSource,
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
    ): ResultWithError<AuthTokens, SignupWithGoogleRemoteDataSourceError> = executeRemoteRequest(
        TAG,
        "signupWithGoogle",
        logger,
        SignupWithGoogleRemoteDataSourceError::RemoteDataSource,
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
    ): ResultWithError<AuthTokens, RegisterRemoteDataSourceError> = executeRemoteRequest(
        TAG,
        "register",
        logger,
        RegisterRemoteDataSourceError::RemoteDataSource,
    ) {
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

    override suspend fun refresh(
        refreshToken: String,
    ): ResultWithError<AuthTokens, RefreshRemoteDataSourceError> = executeRemoteRequest(
        TAG,
        "refresh",
        logger,
        RefreshRemoteDataSourceError::RemoteDataSource,
    ) {
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

    override suspend fun logout(
        accessToken: String,
    ): ResultWithError<Unit, LogoutRemoteDataSourceError> =
        executeRemoteRequest(TAG, "logout", logger, LogoutRemoteDataSourceError::RemoteDataSource) {
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

    private fun mapLoginWithCredentialsError(
        apiError: ApiError?,
    ): LoginWithCredentialsRemoteDataSourceError = when (apiError?.code) {
        CODE_INVALID_CREDENTIALS -> LoginWithCredentialsRemoteDataSourceError.InvalidCredentials
        CODE_EMAIL_NOT_VERIFIED -> LoginWithCredentialsRemoteDataSourceError.EmailNotVerified
        CODE_ACCOUNT_SUSPENDED -> LoginWithCredentialsRemoteDataSourceError.AccountSuspended
        else -> LoginWithCredentialsRemoteDataSourceError.RemoteDataSource(infraError(apiError))
    }

    private fun mapSignupWithGoogleError(
        apiError: ApiError?,
    ): SignupWithGoogleRemoteDataSourceError = when (apiError?.code) {
        CODE_INVALID_TOKEN -> SignupWithGoogleRemoteDataSourceError.InvalidToken
        CODE_ACCOUNT_ALREADY_EXISTS -> SignupWithGoogleRemoteDataSourceError.AccountAlreadyExists
        CODE_INVALID_NAME -> SignupWithGoogleRemoteDataSourceError.InvalidName(
            ProfileNameValidationError.UnknownRuleViolation(apiError.message),
        )
        else -> SignupWithGoogleRemoteDataSourceError.RemoteDataSource(
            infraError(apiError),
        )
    }

    private fun mapLoginWithGoogleError(apiError: ApiError?): LoginWithGoogleRemoteDataSourceError =
        when (apiError?.code) {
            CODE_INVALID_TOKEN -> LoginWithGoogleRemoteDataSourceError.InvalidToken
            CODE_ACCOUNT_NOT_FOUND -> LoginWithGoogleRemoteDataSourceError.AccountNotFound
            CODE_ACCOUNT_SUSPENDED -> LoginWithGoogleRemoteDataSourceError.AccountSuspended
            else -> LoginWithGoogleRemoteDataSourceError.RemoteDataSource(infraError(apiError))
        }

    private fun mapRegisterError(apiError: ApiError?): RegisterRemoteDataSourceError =
        when (apiError?.code) {
            CODE_EMAIL_TAKEN -> RegisterRemoteDataSourceError.InvalidEmail(
                SignupEmailError.EmailTaken,
            )
            CODE_EMAIL_NOT_EXISTS -> RegisterRemoteDataSourceError.InvalidEmail(
                EmailUnknownError(CODE_EMAIL_NOT_EXISTS),
            )
            CODE_PASSWORD_TOO_SHORT -> RegisterRemoteDataSourceError.InvalidPassword(
                PasswordValidationError.PasswordTooShort(
                    apiError.details?.get("min_length")?.toIntOrNull(),
                ),
            )
            CODE_PASSWORD_TOO_LONG -> RegisterRemoteDataSourceError.InvalidPassword(
                PasswordValidationError.PasswordTooLong(
                    apiError.details?.get("max_length")?.toIntOrNull(),
                ),
            )
            CODE_INVALID_NAME -> RegisterRemoteDataSourceError.InvalidName(
                ProfileNameValidationError.UnknownRuleViolation(apiError.message),
            )
            else -> RegisterRemoteDataSourceError.RemoteDataSource(infraError(apiError))
        }

    private fun mapRefreshError(apiError: ApiError?): RefreshRemoteDataSourceError =
        when (apiError?.code) {
            CODE_TOKEN_EXPIRED -> RefreshRemoteDataSourceError.TokenExpired
            CODE_SESSION_REVOKED -> RefreshRemoteDataSourceError.SessionRevoked
            else -> RefreshRemoteDataSourceError.RemoteDataSource(infraError(apiError))
        }

    private fun mapLogoutError(apiError: ApiError?): LogoutRemoteDataSourceError =
        LogoutRemoteDataSourceError.RemoteDataSource(infraError(apiError))
}
