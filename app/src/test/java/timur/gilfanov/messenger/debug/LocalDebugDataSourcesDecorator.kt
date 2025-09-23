package timur.gilfanov.messenger.debug

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSources
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

class LocalDebugDataSourcesDecorator(private val dataSource: LocalDebugDataSources) :
    LocalDebugDataSources by dataSource {

    var debugDeleteAllChatsError: LocalDataSourceError? = null

    var shouldFailGetLastSyncTimestamp = false
    var shouldFailFlowChatList = false

    override suspend fun deleteAllChats(): ResultWithError<Unit, LocalDataSourceError> =
        debugDeleteAllChatsError?.let {
            ResultWithError.Failure(it)
        } ?: dataSource.deleteAllChats()

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
}
