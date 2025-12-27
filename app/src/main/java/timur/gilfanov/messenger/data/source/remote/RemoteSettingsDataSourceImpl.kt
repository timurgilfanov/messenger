package timur.gilfanov.messenger.data.source.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import timur.gilfanov.messenger.data.source.local.toStorageValue
import timur.gilfanov.messenger.data.source.remote.dto.ApiErrorCode
import timur.gilfanov.messenger.data.source.remote.dto.ApiResponse
import timur.gilfanov.messenger.data.source.remote.dto.ChangeSettingsRequestDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingChangeDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingSyncItemDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingSyncResultDto
import timur.gilfanov.messenger.data.source.remote.dto.SettingsResponseDto
import timur.gilfanov.messenger.data.source.remote.dto.SyncSettingsRequestDto
import timur.gilfanov.messenger.data.source.remote.dto.SyncSettingsResponseDto
import timur.gilfanov.messenger.data.source.remote.network.ApiRoutes
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.util.Logger

@Suppress("TooManyFunctions")
@Singleton
class RemoteSettingsDataSourceImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val logger: Logger,
) : RemoteSettingsDataSource {

    companion object {
        private const val TAG = "RemoteSettingsDataSource"
    }

    override suspend fun get(): ResultWithError<RemoteSettings, RemoteSettingsDataSourceError> =
        executeRequest("get") {
            val response: ApiResponse<SettingsResponseDto> = httpClient.get(
                ApiRoutes.SETTINGS,
            ).body()

            if (response.success && response.data != null) {
                val settingDtos = response.data.settings.map { item ->
                    RemoteSettingDto(key = item.key, value = item.value, version = item.version)
                }
                val remoteSettings = RemoteSettings.fromItems(logger, settingDtos)
                ResultWithError.Success(remoteSettings)
            } else {
                ResultWithError.Failure(handleApiError(response))
            }
        }

    override suspend fun changeUiLanguage(
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> =
        executeRequest("changeUiLanguage") {
            val request = ChangeSettingsRequestDto(
                settings = listOf(
                    SettingChangeDto(
                        key = SettingKey.UI_LANGUAGE.key,
                        value = language.toStorageValue(),
                    ),
                ),
            )

            val response: ApiResponse<Unit> = httpClient.put(ApiRoutes.CHANGE_SETTINGS) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            if (response.success) {
                ResultWithError.Success(Unit)
            } else {
                ResultWithError.Failure(handleApiError(response))
            }
        }

    override suspend fun put(
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = executeRequest("put") {
        val request = ChangeSettingsRequestDto(
            settings = listOf(
                SettingChangeDto(
                    key = SettingKey.UI_LANGUAGE.key,
                    value = settings.uiLanguage.toStorageValue(),
                ),
            ),
        )

        val response: ApiResponse<Unit> = httpClient.put(ApiRoutes.CHANGE_SETTINGS) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        if (response.success) {
            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(handleApiError(response))
        }
    }

    override suspend fun syncSingleSetting(
        request: TypedSettingSyncRequest,
    ): ResultWithError<SyncResult, SyncSingleSettingError> {
        val syncResult = executeSyncRequest(listOf(request))
        return when (syncResult) {
            is ResultWithError.Success -> {
                val key = request.toSettingKey()
                val result = syncResult.data[key]
                if (result != null) {
                    ResultWithError.Success(result)
                } else {
                    logger.e(TAG, "Sync response missing result for key: $key")
                    ResultWithError.Failure(
                        RemoteSettingsDataSourceError.RemoteDataSource(
                            RemoteDataSourceErrorV2.UnknownServiceError(
                                ErrorReason("Missing sync result for key: $key"),
                            ),
                        ),
                    )
                }
            }

            is ResultWithError.Failure -> ResultWithError.Failure(syncResult.error)
        }
    }

    override suspend fun syncBatch(
        requests: List<TypedSettingSyncRequest>,
    ): ResultWithError<Map<String, SyncResult>, SyncBatchError> = executeSyncRequest(requests)

    private suspend fun executeSyncRequest(
        requests: List<TypedSettingSyncRequest>,
    ): ResultWithError<Map<String, SyncResult>, RemoteSettingsDataSourceError> =
        executeRequest("sync") {
            val syncItems = requests.map { typedRequest ->
                val baseRequest = typedRequest.request
                SettingSyncItemDto(
                    key = typedRequest.toSettingKey(),
                    value = typedRequest.toStorageValue(),
                    clientVersion = baseRequest.clientVersion,
                    lastKnownServerVersion = baseRequest.lastKnownServerVersion,
                    modifiedAt = baseRequest.modifiedAt,
                )
            }

            val httpRequest = SyncSettingsRequestDto(settings = syncItems)
            val response: ApiResponse<SyncSettingsResponseDto> = httpClient.post(
                ApiRoutes.SYNC_SETTINGS,
            ) {
                contentType(ContentType.Application.Json)
                setBody(httpRequest)
            }.body()

            if (response.success && response.data != null) {
                val results = response.data.results.associate { dto ->
                    dto.key to dto.toSyncResult()
                }
                ResultWithError.Success(results)
            } else {
                ResultWithError.Failure(handleApiError(response))
            }
        }

    private fun SettingSyncResultDto.toSyncResult(): SyncResult = when (this) {
        is SettingSyncResultDto.Success -> SyncResult.Success(newVersion = newVersion)

        is SettingSyncResultDto.Conflict -> SyncResult.Conflict(
            serverValue = serverValue,
            serverVersion = serverVersion,
            newVersion = newVersion,
            serverModifiedAt = serverModifiedAt,
        )
    }

    private fun TypedSettingSyncRequest.toSettingKey(): String = when (this) {
        is TypedSettingSyncRequest.UiLanguage -> SettingKey.UI_LANGUAGE.key
    }

    private fun TypedSettingSyncRequest.toStorageValue(): String = when (this) {
        is TypedSettingSyncRequest.UiLanguage -> request.value.toStorageValue()
    }

    private suspend inline fun <T> executeRequest(
        operationName: String,
        block: () -> ResultWithError<T, RemoteSettingsDataSourceError>,
    ): ResultWithError<T, RemoteSettingsDataSourceError> = try {
        logger.d(TAG, "Executing $operationName")
        block()
    } catch (e: SerializationException) {
        logger.e(TAG, "Failed to serialize/deserialize in $operationName", e)
        ResultWithError.Failure(
            RemoteSettingsDataSourceError.RemoteDataSource(RemoteDataSourceErrorV2.ServerError),
        )
    } catch (e: SocketTimeoutException) {
        logger.e(TAG, "Request timed out in $operationName", e)
        ResultWithError.Failure(
            RemoteSettingsDataSourceError.RemoteDataSource(
                RemoteDataSourceErrorV2.ServiceUnavailable.Timeout,
            ),
        )
    } catch (e: UnknownHostException) {
        logger.e(TAG, "Network not available in $operationName", e)
        ResultWithError.Failure(
            RemoteSettingsDataSourceError.RemoteDataSource(
                RemoteDataSourceErrorV2.ServiceUnavailable.NetworkNotAvailable,
            ),
        )
    } catch (e: ConnectException) {
        logger.e(TAG, "Server unreachable in $operationName", e)
        ResultWithError.Failure(
            RemoteSettingsDataSourceError.RemoteDataSource(
                RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
            ),
        )
    } catch (e: IOException) {
        logger.e(TAG, "Network error in $operationName", e)
        ResultWithError.Failure(
            RemoteSettingsDataSourceError.RemoteDataSource(
                RemoteDataSourceErrorV2.ServiceUnavailable.ServerUnreachable,
            ),
        )
    } catch (e: CancellationException) {
        logger.d(TAG, "$operationName cancelled")
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.e(TAG, "Unexpected error in $operationName", e)
        ResultWithError.Failure(
            RemoteSettingsDataSourceError.RemoteDataSource(
                RemoteDataSourceErrorV2.UnknownServiceError(
                    ErrorReason(e.message ?: "Unknown error"),
                ),
            ),
        )
    }

    private fun handleApiError(response: ApiResponse<*>): RemoteSettingsDataSourceError =
        RemoteSettingsDataSourceError.RemoteDataSource(
            error = when (response.error?.code) {
                ApiErrorCode.AlreadyJoined,

                ApiErrorCode.ChatClosed,

                ApiErrorCode.ChatFull,

                ApiErrorCode.ChatNotFound,

                ApiErrorCode.ExpiredInviteLink,

                ApiErrorCode.InvalidInviteLink,

                ApiErrorCode.MessageNotFound,

                ApiErrorCode.NetworkNotAvailable,
                -> TODO("Not relevant for settings")

                is ApiErrorCode.CooldownActive -> RemoteDataSourceErrorV2.CooldownActive(
                    remaining = response.error.code.remaining,
                )

                ApiErrorCode.RateLimitExceeded -> RemoteDataSourceErrorV2.RateLimitExceeded

                ApiErrorCode.ServerError -> RemoteDataSourceErrorV2.ServerError

                ApiErrorCode.ServerUnreachable,
                ApiErrorCode.Unauthorized,
                -> TODO("Should be handled on HTTP level")

                is ApiErrorCode.Unknown -> {
                    val reason = ErrorReason(response.error.message)
                    RemoteDataSourceErrorV2.UnknownServiceError(reason)
                }

                ApiErrorCode.UserBlocked -> TODO(
                    "Should be only for authorization request? " +
                        "For other request should return HTTP 401?",
                )

                null -> TODO("Should be handled on serialization stage")
            },
        ).also {
            logger.w(TAG, "API error: ${response.error.message} mapped to $it")
        }
}
