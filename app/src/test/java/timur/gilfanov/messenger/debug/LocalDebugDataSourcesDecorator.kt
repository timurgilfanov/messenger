package timur.gilfanov.messenger.debug

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.LocalClearSyncTimestampError
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSources
import timur.gilfanov.messenger.data.source.local.LocalGetSettingsError
import timur.gilfanov.messenger.data.source.local.LocalUpdateSettingsError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

class LocalDebugDataSourcesDecorator(private val dataSource: LocalDebugDataSources) :
    LocalDebugDataSources by dataSource {

    var debugDeleteAllChatsError: LocalDataSourceError? = null
    var debugDeleteAllMessagesError: LocalDataSourceError? = null
    var debugClearSyncTimestampError: LocalClearSyncTimestampError? = null
    var localGetSettingsError: LocalGetSettingsError? = null
    var localUpdateSettingsError: LocalUpdateSettingsError? = null

    var shouldFailGetLastSyncTimestamp = false
    var shouldFailFlowChatList = false

    override suspend fun deleteAllChats(): ResultWithError<Unit, LocalDataSourceError> =
        debugDeleteAllChatsError?.let {
            ResultWithError.Failure(it)
        } ?: dataSource.deleteAllChats()

    override suspend fun deleteAllMessages(): ResultWithError<Unit, LocalDataSourceError> =
        debugDeleteAllMessagesError?.let {
            ResultWithError.Failure(it)
        } ?: dataSource.deleteAllMessages()

    override suspend fun clearSyncTimestamp(): ResultWithError<Unit, LocalClearSyncTimestampError> =
        debugClearSyncTimestampError?.let {
            ResultWithError.Failure(it)
        } ?: dataSource.clearSyncTimestamp()

    override fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, LocalDataSourceError>> =
        dataSource.flowChatList().map {
            if (shouldFailFlowChatList) {
                ResultWithError.Failure(LocalDataSourceError.StorageUnavailable)
            } else {
                it
            }
        }

    override suspend fun getLastSyncTimestamp(): ResultWithError<Instant?, LocalDataSourceError> =
        if (shouldFailGetLastSyncTimestamp) {
            ResultWithError.Failure(LocalDataSourceError.StorageUnavailable)
        } else {
            dataSource.getLastSyncTimestamp()
        }

    override val settings: Flow<ResultWithError<DebugSettings, LocalGetSettingsError>>
        get() = dataSource.settings.map { result ->
            localGetSettingsError?.let { error ->
                ResultWithError.Failure(error)
            } ?: result
        }

    override suspend fun updateSettings(
        transform: (DebugSettings) -> DebugSettings,
    ): ResultWithError<Unit, LocalUpdateSettingsError> = localUpdateSettingsError?.let {
        ResultWithError.Failure(it)
    } ?: dataSource.updateSettings(transform)
}
