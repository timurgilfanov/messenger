package timur.gilfanov.messenger.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.paging.MessagePagingSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDataSources
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.DeleteChatError
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryMarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
import timur.gilfanov.messenger.util.Logger

/**
 * Implementation of [ChatRepository] and [MessageRepository] that manages chats and messages
 * with local caching and real-time synchronization.
 *
 * ## Current Architecture (Chat-Only Sync):
 * - Subscribes to
 * [timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource.chatsDeltaUpdates] for chat synchronization
 * - Delta updates applied to local database via [timur.gilfanov.messenger.data.source.local.LocalSyncDataSource.applyChatListDelta]
 * - HTTP long polling with adaptive delays (2s idle, 0.5s when catching up)
 *
 * ## Planned Architecture (Unified Sync Channel):
 * Will share a unified sync channel with [SettingsRepositoryImpl] to reduce network overhead
 * and provide consistent synchronization timestamps.
 *
 * ### Migration Plan:
 *
 * 1. **Update RemoteSyncDataSource to return unified SyncDelta**:
 *    - Rename `chatsDeltaUpdates()` → `deltaUpdates()`
 *    - Delta now contains both `chatChanges` and `settingsChange`
 *    - Same HTTP polling mechanism, single endpoint
 *
 * 2. **Filter relevant changes in performDeltaSyncLoop()**:
 * ```kotlin
 * private fun performDeltaSyncLoop(scope: CoroutineScope) {
 *     scope.launch(Dispatchers.IO) {
 *         val lastSync = localDataSources.sync.getLastSyncTimestamp().getOrNull()
 *
 *         remoteDataSources.sync.deltaUpdates(lastSync)  // Unified stream
 *             .onEach { result ->
 *                 if (result is ResultWithError.Success) {
 *                     val delta = result.data
 *
 *                     // Process chat changes (this repository's responsibility)
 *                     if (delta.chatChanges.isNotEmpty()) {
 *                         isUpdatingFlow.value = true
 *                         localDataSources.sync.applyChatListDelta(
 *                             ChatListDelta(
 *                                 changes = delta.chatChanges,
 *                                 toTimestamp = delta.toTimestamp,
 *                                 hasMoreChanges = delta.hasMoreChanges
 *                             )
 *                         )
 *                         isUpdatingFlow.value = false
 *                     }
 *
 *                     // Note: settingsChange filtered out (SettingsRepository handles it)
 *                 }
 *             }
 *             .catch { e -> logger.e(TAG, "Error collecting deltaUpdates", e) }
 *             .collect()
 *     }
 * }
 * ```
 *
 * 3. **Benefits**:
 *    - Single HTTP polling connection shared between repositories
 *    - Consistent `toTimestamp` across all entity types
 *    - Atomic updates (chat + settings changed together arrive together)
 *    - Reduced battery/data usage
 *    - Simpler testing (single source to mock)
 *
 * 4. **Backward Compatibility**:
 *    - Keep `chatsDeltaUpdates()` deprecated during migration
 *    - Both methods can coexist temporarily
 *    - Remove old method after all consumers migrated
 *
 * @see timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource for unified sync channel details
 * @see SettingsRepositoryImpl for settings sync implementation
 */
@Singleton
@Suppress("TooManyFunctions") // Combines ChatRepository and MessageRepository interfaces
class MessengerRepositoryImpl @Inject constructor(
    private val localDataSources: LocalDataSources,
    private val remoteDataSources: RemoteDataSources,
    private val logger: Logger,
    backgroundScope: CoroutineScope,
) : ChatRepository,
    MessageRepository {

    companion object {
        private const val TAG = "MessengerRepository"
    }

    private val isUpdatingFlow = MutableStateFlow(false)

    init {
        performDeltaSyncLoop(backgroundScope)
    }

    private fun performDeltaSyncLoop(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            logger.d(TAG, "Starting delta sync loop")
            val lastSyncTimestamp =
                when (val result = localDataSources.sync.getLastSyncTimestamp()) {
                    is ResultWithError.Success -> result.data
                    is ResultWithError.Failure -> {
                        logger.w(TAG, "Failed to get last sync timestamp: ${result.error}")
                        null // Start from scratch if no timestamp available
                    }
                }
            logger.d(TAG, "Last sync timestamp: $lastSyncTimestamp")
            remoteDataSources.sync.chatsDeltaUpdates(lastSyncTimestamp)
                .onEach { deltaResult ->
                    if (deltaResult is ResultWithError.Success) {
                        logger.d(TAG, "Received delta updates: ${deltaResult.data}")
                        isUpdatingFlow.value = true
                        // todo should map local error
                        localDataSources.sync.applyChatListDelta(deltaResult.data)
                        isUpdatingFlow.value = false
                    } else {
                        logger.w(TAG, "Delta result was failure: $deltaResult")
                    }
                }
                .catch { e ->
                    logger.e(TAG, "Error collecting chatsDeltaUpdates", e)
                }
                .collect()
        }
    }

    // ChatRepository implementation
    override suspend fun createChat(chat: Chat): ResultWithError<Chat, CreateChatError> =
        when (val result = remoteDataSources.chat.createChat(chat)) {
            is ResultWithError.Success -> {
                localDataSources.chat.insertChat(result.data) // todo should map local error
                ResultWithError.Success(result.data)
            }

            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToCreateChatError(result.error))
            }
        }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, DeleteChatError> =
        when (val result = remoteDataSources.chat.deleteChat(chatId)) {
            is ResultWithError.Success -> {
                localDataSources.chat.deleteChat(chatId) // todo should map local error
                ResultWithError.Success(Unit)
            }

            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToDeleteChatError(result.error, chatId))
            }
        }

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, JoinChatError> =
        when (val result = remoteDataSources.chat.joinChat(chatId, inviteLink)) {
            is ResultWithError.Success -> {
                localDataSources.chat.insertChat(result.data) // todo should map local error
                ResultWithError.Success(result.data)
            }

            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToJoinChatError(result.error))
            }
        }

    override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, LeaveChatError> =
        when (val result = remoteDataSources.chat.leaveChat(chatId)) {
            is ResultWithError.Success -> {
                localDataSources.chat.deleteChat(chatId) // todo should map local error
                ResultWithError.Success(Unit)
            }

            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToLeaveChatError(result.error))
            }
        }

    override suspend fun flowChatList(): Flow<
        ResultWithError<
            List<ChatPreview>,
            FlowChatListError,
            >,
        > =
        localDataSources.chat.flowChatList().map { localResult ->
            when (localResult) {
                is ResultWithError.Success -> ResultWithError.Success(localResult.data)
                is ResultWithError.Failure -> ResultWithError.Failure(
                    FlowChatListError.LocalOperationFailed(
                        when (localResult.error) {
                            LocalDataSourceError.StorageUnavailable -> LocalStorageError.Corrupted
                            else -> LocalStorageError.UnknownError(
                                TODO(
                                    "use localResult.error after local " +
                                        "data source errors refactoring",
                                ),
                            )
                        },
                    ),
                )
            }
        }

    override fun isChatListUpdating(): Flow<Boolean> = isUpdatingFlow

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> =
        localDataSources.chat.flowChatUpdates(chatId).map { localResult ->
            when (localResult) {
                is ResultWithError.Success -> ResultWithError.Success(localResult.data)
                is ResultWithError.Failure -> ResultWithError.Failure(ChatNotFound)
            }
        }

    override suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, RepositoryMarkMessagesAsReadError> =
        when (val result = remoteDataSources.chat.markMessagesAsRead(chatId, upToMessageId)) {
            is ResultWithError.Success -> {
                // The delta sync loop will pick up the chat updates automatically
                ResultWithError.Success(Unit)
            }

            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToMarkMessagesAsReadError(result.error))
            }
        }

    // MessageRepository implementation
    override suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, SendMessageError>> {
        localDataSources.message.insertMessage(message) // todo should map local error

        return remoteDataSources.message.sendMessage(message).map { result ->
            when (result) {
                is ResultWithError.Success -> {
                    // todo should map local error
                    localDataSources.message.updateMessage(result.data)
                    ResultWithError.Success(result.data)
                }

                is ResultWithError.Failure -> {
                    ResultWithError.Failure(mapRemoteErrorToSendMessageError(result.error))
                }
            }
        }
    }

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, EditMessageError>> =
        remoteDataSources.message.editMessage(message).map { result ->
            when (result) {
                is ResultWithError.Success -> {
                    // todo should map local error
                    localDataSources.message.updateMessage(result.data)
                    ResultWithError.Success(result.data)
                }

                is ResultWithError.Failure -> {
                    ResultWithError.Failure(mapRemoteErrorToEditMessageError(result.error))
                }
            }
        }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, DeleteMessageError> =
        when (val result = remoteDataSources.message.deleteMessage(messageId, mode)) {
            is ResultWithError.Success -> {
                localDataSources.message.deleteMessage(messageId) // todo should map local error
                ResultWithError.Success(Unit)
            }

            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToDeleteMessageError(result.error))
            }
        }

    override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> = Pager(
        config = PagingConfig(
            pageSize = MessagePagingSource.DEFAULT_PAGE_SIZE,
            prefetchDistance = MessagePagingSource.PREFETCH_DISTANCE,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            localDataSources.message.getMessagePagingSource(chatId)
        },
    ).flow
}

// Error mapping functions
private fun mapRemoteErrorToCreateChatError(error: RemoteDataSourceError): CreateChatError =
    CreateChatError.RemoteOperationFailed(
        when (error) {
            RemoteDataSourceError.NetworkNotAvailable -> RemoteError.Failed.NetworkNotAvailable
            RemoteDataSourceError.ServerUnreachable -> RemoteError.Failed.ServiceDown
            RemoteDataSourceError.ServerError -> RemoteError.Failed.ServiceDown
            RemoteDataSourceError.Unauthorized -> RemoteError.Unauthenticated
            is RemoteDataSourceError.UnknownError -> RemoteError.Failed.UnknownServiceError(
                ErrorReason("Unknown remote error: ${error.cause}"),
            )

            else -> TODO("Implement after remote data source operation error refactor")
        },
    )

private fun mapRemoteErrorToDeleteChatError(
    error: RemoteDataSourceError,
    chatId: ChatId,
): DeleteChatError = when (error) {
    RemoteDataSourceError.NetworkNotAvailable -> DeleteChatError.RemoteOperationFailed(
        RemoteError.Failed.NetworkNotAvailable,
    )

    RemoteDataSourceError.ServerUnreachable -> DeleteChatError.RemoteOperationFailed(
        RemoteError.Failed.ServiceDown,
    )

    RemoteDataSourceError.ServerError -> DeleteChatError.RemoteOperationFailed(
        RemoteError.Failed.ServiceDown,
    )

    RemoteDataSourceError.Unauthorized -> DeleteChatError.RemoteOperationFailed(
        RemoteError.Unauthenticated,
    )

    is RemoteDataSourceError.UnknownError -> DeleteChatError.RemoteOperationFailed(
        RemoteError.Failed.UnknownServiceError(
            ErrorReason("Unknown remote error: ${error.cause}"),
        ),
    )

    RemoteDataSourceError.ChatNotFound -> DeleteChatError.ChatNotFound(chatId)

    else -> TODO("Implement after remote data source operation error refactor")
}

@Suppress("CyclomaticComplexMethod")
private fun mapRemoteErrorToJoinChatError(error: RemoteDataSourceError): JoinChatError =
    when (error) {
        RemoteDataSourceError.AlreadyJoined -> JoinChatError.AlreadyJoined
        RemoteDataSourceError.ChatClosed -> JoinChatError.ChatClosed
        RemoteDataSourceError.ChatFull -> JoinChatError.ChatFull
        RemoteDataSourceError.ChatNotFound -> JoinChatError.ChatNotFound
        is RemoteDataSourceError.CooldownActive -> JoinChatError.RemoteOperationFailed(
            RemoteError.Failed.Cooldown(error.remaining),
        )

        RemoteDataSourceError.ExpiredInviteLink -> JoinChatError.ExpiredInviteLink
        RemoteDataSourceError.InvalidInviteLink -> JoinChatError.InvalidInviteLink
        RemoteDataSourceError.NetworkNotAvailable -> JoinChatError.RemoteOperationFailed(
            RemoteError.Failed.NetworkNotAvailable,
        )

        RemoteDataSourceError.RateLimitExceeded -> JoinChatError.RemoteOperationFailed(
            RemoteError.Failed.ServiceDown,
        )

        RemoteDataSourceError.ServerError -> JoinChatError.RemoteOperationFailed(
            RemoteError.Failed.ServiceDown,
        )

        RemoteDataSourceError.ServerUnreachable -> JoinChatError.RemoteOperationFailed(
            RemoteError.Failed.ServiceDown,
        )

        RemoteDataSourceError.Unauthorized -> JoinChatError.RemoteOperationFailed(
            RemoteError.Unauthenticated,
        )

        is RemoteDataSourceError.UnknownError -> JoinChatError.RemoteOperationFailed(
            RemoteError.Failed.UnknownServiceError(
                ErrorReason("Unknown remote error: ${error.cause}"),
            ),
        )

        RemoteDataSourceError.UserBlocked -> JoinChatError.UserBlocked
        else -> TODO("Implement after remote data source operation error refactor")
    }

private fun mapRemoteErrorToLeaveChatError(error: RemoteDataSourceError): LeaveChatError =
    when (error) {
        RemoteDataSourceError.NetworkNotAvailable -> LeaveChatError.RemoteOperationFailed(
            RemoteError.Failed.NetworkNotAvailable,
        )

        RemoteDataSourceError.ServerUnreachable -> LeaveChatError.RemoteOperationFailed(
            RemoteError.Failed.ServiceDown,
        )

        RemoteDataSourceError.ServerError -> LeaveChatError.RemoteOperationFailed(
            RemoteError.Failed.ServiceDown,
        )

        RemoteDataSourceError.Unauthorized -> LeaveChatError.RemoteOperationFailed(
            RemoteError.Unauthenticated,
        )

        is RemoteDataSourceError.UnknownError -> LeaveChatError.RemoteOperationFailed(
            RemoteError.Failed.UnknownServiceError(
                ErrorReason("Unknown remote error: ${error.cause}"),
            ),
        )

        RemoteDataSourceError.ChatNotFound -> LeaveChatError.ChatNotFound

        else -> TODO("Implement after remote data source operation error refactor")
    }

private fun mapRemoteErrorToSendMessageError(error: RemoteDataSourceError): SendMessageError =
    SendMessageError.RemoteOperationFailed(
        when (error) {
            RemoteDataSourceError.NetworkNotAvailable -> RemoteError.Failed.NetworkNotAvailable
            RemoteDataSourceError.ServerUnreachable -> RemoteError.Failed.ServiceDown
            RemoteDataSourceError.ServerError -> RemoteError.Failed.ServiceDown
            RemoteDataSourceError.Unauthorized -> RemoteError.Unauthenticated
            is RemoteDataSourceError.UnknownError -> RemoteError.Failed.UnknownServiceError(
                ErrorReason("Unknown remote error: ${error.cause}"),
            )

            else -> TODO("Implement after remote data source operation error refactor")
        },
    )

private fun mapRemoteErrorToEditMessageError(error: RemoteDataSourceError): EditMessageError =
    EditMessageError.RemoteOperationFailed(
        when (error) {
            RemoteDataSourceError.NetworkNotAvailable -> RemoteError.Failed.NetworkNotAvailable
            RemoteDataSourceError.ServerUnreachable -> RemoteError.Failed.ServiceDown
            RemoteDataSourceError.ServerError -> RemoteError.Failed.ServiceDown
            RemoteDataSourceError.Unauthorized -> RemoteError.Unauthenticated
            is RemoteDataSourceError.UnknownError -> RemoteError.Failed.UnknownServiceError(
                ErrorReason("Unknown remote error: ${error.cause}"),
            )

            else -> TODO("Implement after remote data source operation error refactor")
        },
    )

private fun mapRemoteErrorToDeleteMessageError(error: RemoteDataSourceError): DeleteMessageError =
    when (error) {
        RemoteDataSourceError.NetworkNotAvailable -> DeleteMessageError.RemoteOperationFailed(
            RemoteError.Failed.NetworkNotAvailable,
        )

        RemoteDataSourceError.ServerUnreachable -> DeleteMessageError.RemoteOperationFailed(
            RemoteError.Failed.ServiceDown,
        )

        RemoteDataSourceError.ServerError -> DeleteMessageError.RemoteOperationFailed(
            RemoteError.Failed.ServiceDown,
        )

        RemoteDataSourceError.Unauthorized -> DeleteMessageError.RemoteOperationFailed(
            RemoteError.Unauthenticated,
        )

        is RemoteDataSourceError.UnknownError -> DeleteMessageError.RemoteOperationFailed(
            RemoteError.Failed.UnknownServiceError(
                ErrorReason("Unknown remote error: ${error.cause}"),
            ),
        )

        RemoteDataSourceError.MessageNotFound -> DeleteMessageError.MessageNotFound

        else -> TODO("Implement after remote data source operation error refactor")
    }

private fun mapRemoteErrorToMarkMessagesAsReadError(
    error: RemoteDataSourceError,
): RepositoryMarkMessagesAsReadError = when (error) {
    RemoteDataSourceError.NetworkNotAvailable -> MarkMessagesAsReadError.RemoteOperationFailed(
        RemoteError.Failed.NetworkNotAvailable,
    )

    RemoteDataSourceError.ServerUnreachable -> MarkMessagesAsReadError.RemoteOperationFailed(
        RemoteError.Failed.ServiceDown,
    )

    RemoteDataSourceError.ServerError -> MarkMessagesAsReadError.RemoteOperationFailed(
        RemoteError.Failed.ServiceDown,
    )

    RemoteDataSourceError.Unauthorized -> MarkMessagesAsReadError.RemoteOperationFailed(
        RemoteError.Unauthenticated,
    )

    is RemoteDataSourceError.UnknownError -> MarkMessagesAsReadError.RemoteOperationFailed(
        RemoteError.Failed.UnknownServiceError(
            ErrorReason("Unknown remote error: ${error.cause}"),
        ),
    )

    RemoteDataSourceError.ChatNotFound -> MarkMessagesAsReadError.ChatNotFound

    else -> MarkMessagesAsReadError.RemoteOperationFailed(
        RemoteError.Failed.UnknownServiceError(
            ErrorReason("Unknown remote error: $error"),
        ),
    )
}
