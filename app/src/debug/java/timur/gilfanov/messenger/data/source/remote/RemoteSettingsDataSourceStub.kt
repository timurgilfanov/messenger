package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

class RemoteSettingsDataSourceStub : RemoteSettingsDataSource {

    private var getResponse: ResultWithError<RemoteSettings, RemoteSettingsDataSourceError>? = null
    private var changeUiLanguageResponse:
        ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError>? = null
    private var putResponse: ResultWithError<Unit, UpdateSettingsRemoteDataSourceError>? = null
    private var syncSingleResponse: ResultWithError<SyncResult, SyncSingleSettingError>? = null
    private var syncBatchResponse: ResultWithError<Map<String, SyncResult>, SyncBatchError>? = null

    fun setGetResponse(response: ResultWithError<RemoteSettings, RemoteSettingsDataSourceError>) {
        getResponse = response
    }

    fun setChangeUiLanguageResponse(
        response: ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError>,
    ) {
        changeUiLanguageResponse = response
    }

    fun setPutResponse(response: ResultWithError<Unit, UpdateSettingsRemoteDataSourceError>) {
        putResponse = response
    }

    fun setSyncSingleResponse(response: ResultWithError<SyncResult, SyncSingleSettingError>) {
        syncSingleResponse = response
    }

    fun setSyncBatchResponse(response: ResultWithError<Map<String, SyncResult>, SyncBatchError>) {
        syncBatchResponse = response
    }

    override suspend fun get() = getResponse ?: error("get() called but no response configured")

    override suspend fun changeUiLanguage(language: UiLanguage) =
        changeUiLanguageResponse ?: error("changeUiLanguage() called but no response configured")

    override suspend fun put(settings: Settings) =
        putResponse ?: error("put() called but no response configured")

    override suspend fun syncSingleSetting(request: TypedSettingSyncRequest) =
        syncSingleResponse ?: error("syncSingleSetting() called but no response configured")

    override suspend fun syncBatch(requests: List<TypedSettingSyncRequest>) =
        syncBatchResponse ?: error("syncBatch() called but no response configured")
}
