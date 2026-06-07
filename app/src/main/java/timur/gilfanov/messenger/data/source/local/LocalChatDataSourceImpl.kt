package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao
import timur.gilfanov.messenger.data.source.local.database.entity.ChatWithParticipants
import timur.gilfanov.messenger.data.source.local.database.entity.ChatWithParticipantsAndMessages
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.util.Logger

class LocalChatDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val participantDao: ParticipantDao,
    logger: Logger,
) : LocalChatDataSource {

    private val errorHandler = DatabaseErrorHandler(logger)

    override suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> {
        val validationError = validateChatForInsert(chat)
        if (validationError != null) {
            return ResultWithError.Failure(validationError)
        }

        return try {
            database.withTransaction {
                val chatEntity = with(EntityMappers) { chat.toChatEntity() }
                chatDao.insertChat(chatEntity)

                // Insert global participant identities
                val participantEntities = chat.participants.map { participant ->
                    with(EntityMappers) { participant.toParticipantEntity() }
                }
                participantDao.insertParticipants(participantEntities)

                // Insert chat-specific participant relationships
                val crossRefs = chat.participants.map { participant ->
                    with(EntityMappers) {
                        participant.toChatParticipantCrossRef(chat.id.id.toString())
                    }
                }
                chatDao.insertChatParticipantCrossRefs(crossRefs)
            }

            ResultWithError.Success(chat)
        } catch (e: SQLiteException) {
            ResultWithError.Failure(errorHandler.mapException(e))
        }
    }

    override suspend fun updateChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> {
        val validationError = validateChatForUpdate(chat)
        if (validationError != null) {
            return ResultWithError.Failure(validationError)
        }

        return try {
            database.withTransaction {
                val chatEntity = with(EntityMappers) { chat.toChatEntity() }
                chatDao.updateChat(chatEntity)

                chatDao.removeAllChatParticipants(chat.id.id.toString())

                // Insert/update global participant identities
                val participantEntities = chat.participants.map { participant ->
                    with(EntityMappers) { participant.toParticipantEntity() }
                }
                participantDao.insertParticipants(participantEntities)

                // Insert new chat-specific participant relationships
                val crossRefs = chat.participants.map { participant ->
                    with(EntityMappers) {
                        participant.toChatParticipantCrossRef(chat.id.id.toString())
                    }
                }
                chatDao.insertChatParticipantCrossRefs(crossRefs)
            }

            ResultWithError.Success(chat)
        } catch (e: SQLiteException) {
            ResultWithError.Failure(errorHandler.mapException(e))
        }
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, LocalDataSourceError> =
        try {
            database.withTransaction {
                val chatEntity = chatDao.getChatById(chatId.id.toString())
                if (chatEntity != null) {
                    chatDao.deleteChat(chatEntity)
                    participantDao.deleteOrphanedParticipants()
                    ResultWithError.Success(Unit)
                } else {
                    ResultWithError.Failure(
                        LocalDataSourceError.ChatNotFound,
                    )
                }
            }
        } catch (e: SQLiteException) {
            ResultWithError.Failure(errorHandler.mapException(e))
        }

    override fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, LocalDataSourceError>> =
        combine(
            chatDao.flowAllChatsWithParticipants(),
            messageDao.flowLastMessagesPerChat(),
        ) { chatsWithParticipants, lastMessages ->
            val lastMessageByChatId = lastMessages.associateBy { it.chatId }
            val result: ResultWithError<List<ChatPreview>, LocalDataSourceError> =
                if (findOrphanLastMessage(chatsWithParticipants, lastMessageByChatId) != null) {
                    // The two flows are invalidated independently, so combine can transiently
                    // pair a fresh last-message with a stale participant set that does not yet
                    // contain its sender. That apparent inconsistency may be a transient torn
                    // pair or a genuine source-of-truth violation; re-read a single committed
                    // snapshot to tell them apart.
                    readChatPreviewsConsistently()
                } else {
                    ResultWithError.Success(
                        buildChatPreviews(chatsWithParticipants, lastMessageByChatId),
                    )
                }
            result
        }
            .distinctUntilChanged()
            .catch { e ->
                when (e) {
                    is SQLiteException -> emit(
                        ResultWithError.Failure(errorHandler.mapException(e)),
                    )

                    else -> throw e
                }
            }

    private suspend fun readChatPreviewsConsistently() = database.withTransaction {
        val chatsWithParticipants = chatDao.getAllChatsWithParticipants()
        val lastMessageByChatId = messageDao.getLastMessagesPerChat().associateBy { it.chatId }
        val orphan = findOrphanLastMessage(chatsWithParticipants, lastMessageByChatId)
        val result: ResultWithError<List<ChatPreview>, LocalDataSourceError> =
            if (orphan != null) {
                // The orphan persists in a single committed snapshot, so this is a real
                // source-of-truth inconsistency rather than a transient torn read.
                ResultWithError.Failure(
                    LocalDataSourceError.InvalidData(
                        "messages",
                        "Sender ${orphan.senderId} of message ${orphan.id} is not a " +
                            "participant of chat ${orphan.chatId}",
                    ),
                )
            } else {
                ResultWithError.Success(
                    buildChatPreviews(chatsWithParticipants, lastMessageByChatId),
                )
            }
        result
    }

    private fun buildChatPreviews(
        chatsWithParticipants: List<ChatWithParticipants>,
        lastMessageByChatId: Map<String, MessageEntity>,
    ): List<ChatPreview> = chatsWithParticipants.map { chatWithParticipants ->
        val lastMessage = lastMessageByChatId[chatWithParticipants.chat.id]
        with(EntityMappers) { chatWithParticipants.toChatPreview(lastMessage) }
    }

    private fun findOrphanLastMessage(
        chatsWithParticipants: List<ChatWithParticipants>,
        lastMessageByChatId: Map<String, MessageEntity>,
    ): MessageEntity? = chatsWithParticipants.firstNotNullOfOrNull { chatWithParticipants ->
        val lastMessage = lastMessageByChatId[chatWithParticipants.chat.id]
        lastMessage?.takeIf { message ->
            chatWithParticipants.participants.none { it.id == message.senderId }
        }
    }

    /**
     * Emits the chat and its last message whenever either changes.
     *
     * This joins two independently-invalidated Room flows: the outer captured participant snapshot
     * ([ChatDao.flowChatWithParticipants]) and the inner last-message flow
     * ([MessageDao.flowLastMessageInChat]). After a sync transaction that atomically adds a
     * participant and a message from that participant, the inner flow can re-emit a fresh
     * last-message against a still-stale captured participant set, making the sender appear to be a
     * non-participant. This has two indistinguishable causes at flow level: a transient torn read
     * (the committed DB is actually consistent) or a real source-of-truth orphan.
     *
     * To discriminate without silencing real orphans:
     * - **Fast path**: when the captured participant snapshot already contains the last message's
     *   sender, map directly with no extra DB read.
     * - **Verify path**: when an apparent orphan is seen, [readChatUpdateConsistently] re-reads
     *   participants and last message inside one `withTransaction`. If the orphan is gone it was a
     *   transient torn read and emits [ResultWithError.Success]; if it persists it is a real
     *   inconsistency and emits a handled [LocalDataSourceError.InvalidData] [ResultWithError.Failure],
     *   keeping the flow alive and the error visible. Escalation (not a silent skip) is correct
     *   because a real orphan would otherwise blank the chat forever.
     *
     * Note: with a single real Room DB the verify path's transient-recovery branch is not
     * deterministically forceable through this public flow — a genuinely consistent DB always
     * takes the fast path, and the only way to observe a persisting orphan is to seed a real one,
     * which escalates. The recovery branch is therefore not unit-forceable here.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun flowChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, LocalDataSourceError>> =
        chatDao.flowChatWithParticipants(chatId.id.toString())
            .distinctUntilChanged()
            .flatMapLatest { chatWithParticipants ->
                if (chatWithParticipants == null) {
                    flowOf(
                        ResultWithError.Failure<Chat, LocalDataSourceError>(
                            LocalDataSourceError.ChatNotFound,
                        ),
                    )
                } else {
                    val currentUserCrossRefs = chatWithParticipants.participantCrossRefs
                        .filter { it.isCurrentUser }
                    if (currentUserCrossRefs.size != 1) {
                        return@flatMapLatest flowOf(
                            ResultWithError.Failure<Chat, LocalDataSourceError>(
                                LocalDataSourceError.InvalidData(
                                    "participants",
                                    "Expected exactly one current user participant, found ${currentUserCrossRefs.size}",
                                ),
                            ),
                        )
                    }
                    messageDao.flowLastMessageInChat(chatId.id.toString())
                        .distinctUntilChanged()
                        .map { lastMessageEntity ->
                            if (findOrphanLastMessage(chatWithParticipants, lastMessageEntity)
                                != null
                            ) {
                                readChatUpdateConsistently(chatId.id.toString())
                            } else {
                                val chat = with(EntityMappers) {
                                    chatWithParticipants.toChat(listOfNotNull(lastMessageEntity))
                                }
                                ResultWithError.Success(chat)
                            }
                        }
                }
            }
            .catch { e ->
                when (e) {
                    is SQLiteException -> emit(
                        ResultWithError.Failure(errorHandler.mapException(e)),
                    )

                    else -> throw e
                }
            }

    /**
     * Returns [lastMessage] when it is non-null and its sender is absent from
     * [chatWithParticipants]'s captured participant set (an apparent orphan), else null.
     */
    private fun findOrphanLastMessage(
        chatWithParticipants: ChatWithParticipants,
        lastMessage: MessageEntity?,
    ): MessageEntity? = lastMessage?.takeIf { message ->
        chatWithParticipants.participants.none { it.id == message.senderId }
    }

    /**
     * Re-reads participants and last message for [chatId] inside a single committed transaction to
     * discriminate a transient torn read from a real source-of-truth orphan. Emits
     * [LocalDataSourceError.InvalidData] only when the orphan persists against the consistent
     * snapshot; otherwise builds the chat from the re-read snapshot.
     */
    private suspend fun readChatUpdateConsistently(
        chatId: String,
    ): ResultWithError<Chat, LocalDataSourceError> = database.withTransaction {
        val chatWithParticipants = chatDao.getChatWithParticipants(chatId)
            ?: return@withTransaction ResultWithError.Failure(
                LocalDataSourceError.ChatNotFound,
            )
        val lastMessage = messageDao.getLastMessageInChat(chatId)
        val orphan = findOrphanLastMessage(chatWithParticipants, lastMessage)
        if (orphan != null) {
            ResultWithError.Failure(
                LocalDataSourceError.InvalidData(
                    "messages",
                    "Sender ${orphan.senderId} of message ${orphan.id} is not a participant " +
                        "of chat $chatId",
                ),
            )
        } else {
            val chat = with(EntityMappers) {
                chatWithParticipants.toChat(listOfNotNull(lastMessage))
            }
            ResultWithError.Success(chat)
        }
    }

    private fun validateChatForInsert(chat: Chat): LocalDataSourceError? = when {
        chat.name.isBlank() ->
            LocalDataSourceError.InvalidData("name", "Chat name cannot be blank")

        chat.participants.isEmpty() ->
            LocalDataSourceError.InvalidData(
                "participants",
                "Chat must have at least one participant",
            )

        else -> null
    }

    private fun validateChatForUpdate(chat: Chat): LocalDataSourceError? =
        validateChatForInsert(chat)
}
