package timur.gilfanov.messenger.profile.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.data.remote.ApiError
import timur.gilfanov.messenger.data.remote.ApiResponse
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.remote.executeRemoteRequest
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.util.Logger

@Singleton
class RemoteProfileDataSourceImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val logger: Logger,
) : RemoteProfileDataSource {
    companion object {
        private const val TAG = "RemoteProfileDataSource"
        private const val CODE_TOKEN_MISSING = "TOKEN_MISSING"
        private const val CODE_TOKEN_EXPIRED = "TOKEN_EXPIRED"
        private const val CODE_TOKEN_INVALID = "TOKEN_INVALID"
        private const val CODE_SESSION_REVOKED = "SESSION_REVOKED"
        private const val CODE_INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS"
        private const val CODE_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
        private const val CODE_COOLDOWN_ACTIVE = "COOLDOWN_ACTIVE"
        private const val CODE_SERVER_ERROR = "SERVER_ERROR"
        private const val COOLDOWN_REMAINING_MS_KEY = "remaining_ms"
    }

    override suspend fun getCurrentProfile(): ResultWithError<
        Profile,
        RemoteProfileDataSourceError,
        > =
        executeRemoteRequest(
            TAG,
            "getCurrentProfile",
            logger,
            RemoteProfileDataSourceError::RemoteDataSource,
        ) {
            val response: ApiResponse<ProfileDto> = httpClient.get(ProfileApiRoutes.PROFILE).body()
            val data = response.data
            when {
                response.success && data != null -> ResultWithError.Success(data.toDomain())
                response.success -> ResultWithError.Failure(missingDataError())
                else -> ResultWithError.Failure(mapApiError(response.error))
            }
        }

    override suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, UpdateNameRemoteDataSourceError> = ResultWithError.Failure(
        UpdateNameRemoteDataSourceError.RemoteProfileDataSource(editNotImplementedError()),
    )

    override suspend fun updatePicture(
        userId: UserId,
        pictureStream: InputStream,
        mimeType: String,
        contentLength: Long,
    ): Flow<ResultWithError<UploadProgress, UpdatePictureRemoteDataSourceError>> = flowOf(
        ResultWithError.Failure(
            UpdatePictureRemoteDataSourceError.RemoteProfileDataSource(
                editNotImplementedError(),
            ),
        ),
    )

    override suspend fun removePicture(
        userId: UserId,
        pictureUrl: String,
    ): ResultWithError<Unit, RemovePictureRemoteDataSourceError> = ResultWithError.Failure(
        RemovePictureRemoteDataSourceError.RemoteProfileDataSource(editNotImplementedError()),
    )

    private fun editNotImplementedError(): RemoteProfileDataSourceError =
        RemoteProfileDataSourceError.RemoteDataSource(
            RemoteDataSourceError.UnknownServiceError(
                ErrorReason("Profile edit operations are not implemented in the retrieval layer"),
            ),
        )

    private fun missingDataError(): RemoteProfileDataSourceError =
        RemoteProfileDataSourceError.RemoteDataSource(
            RemoteDataSourceError.UnknownServiceError(
                ErrorReason("Profile response missing data"),
            ),
        )

    private fun mapApiError(apiError: ApiError?): RemoteProfileDataSourceError =
        when (apiError?.code) {
            CODE_TOKEN_MISSING -> RemoteProfileDataSourceError.Authentication.TokenMissing
            CODE_TOKEN_EXPIRED -> RemoteProfileDataSourceError.Authentication.TokenExpired
            CODE_TOKEN_INVALID -> RemoteProfileDataSourceError.Authentication.TokenInvalid
            CODE_SESSION_REVOKED -> RemoteProfileDataSourceError.Authentication.SessionRevoked
            CODE_INSUFFICIENT_PERMISSIONS -> RemoteProfileDataSourceError.InsufficientPermissions
            else -> RemoteProfileDataSourceError.RemoteDataSource(infraError(apiError))
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
}
