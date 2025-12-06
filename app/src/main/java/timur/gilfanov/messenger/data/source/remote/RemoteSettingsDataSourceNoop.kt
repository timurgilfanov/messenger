package timur.gilfanov.messenger.data.source.remote

import javax.inject.Inject
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.ErrorReason

/**
 * Temporary placeholder implementation until real remote settings API is available.
 *
 * Returns service unavailable for all operations so the repository can fall back
 * to local defaults without crashing the app.
 */
class RemoteSettingsDataSourceNoop @Inject constructor() : RemoteSettingsDataSource {
    private val unavailableError = RemoteUserDataSourceError.RemoteDataSource(
        RemoteDataSourceErrorV2.UnknownServiceError(
            ErrorReason("Remote settings API not implemented"),
        ),
    )

    private fun <T> unavailable(): ResultWithError<T, RemoteUserDataSourceError> =
        ResultWithError.Failure(unavailableError)

    override suspend fun get(
        identity: Identity,
    ): ResultWithError<RemoteSettings, RemoteUserDataSourceError> = unavailable()

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError> = unavailable()

    override suspend fun put(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError> = unavailable()

    override suspend fun syncSingleSetting(
        request: TypedSettingSyncRequest,
    ): ResultWithError<SyncResult, SyncSingleSettingError> = unavailable()

    override suspend fun syncBatch(
        requests: List<TypedSettingSyncRequest>,
    ): ResultWithError<Map<String, SyncResult>, SyncBatchError> = unavailable()
}
