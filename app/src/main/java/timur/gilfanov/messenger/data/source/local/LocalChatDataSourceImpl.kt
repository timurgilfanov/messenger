package timur.gilfanov.messenger.data.source.local

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.ChatWithParticipantsAndMessages
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

@Suppress("TooGenericExceptionCaught")
class LocalChatDataSourceImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val participantDao: ParticipantDao,
) : LocalChatDataSource {

    override suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> = try {
        val chatEntity = with(EntityMappers) { chat.toChatEntity() }
        chatDao.insertChat(chatEntity)

        // Insert participants with their associations to this chat
        val participantEntities = chat.participants.map { participant ->
            with(EntityMappers) { participant.toParticipantEntity() }
        }
        participantDao.insertParticipants(participantEntities)

        // Insert chat-participant associations
        chat.participants.forEach { participant ->
            chatDao.insertChatParticipantCrossRef(
                ChatParticipantCrossRef(
                    chatId = chat.id.id.toString(),
                    participantId = participant.id.id.toString(),
                ),
            )
        }

        ResultWithError.Success(chat)
    } catch (e: Exception) {
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }

    override suspend fun updateChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> = try {
        val chatEntity = with(EntityMappers) { chat.toChatEntity() }
        chatDao.updateChat(chatEntity)

        // Update participants - delete existing associations and re-insert
        chatDao.removeAllChatParticipants(chat.id.id.toString())

        val participantEntities = chat.participants.map { participant ->
            with(EntityMappers) { participant.toParticipantEntity() }
        }
        participantDao.insertParticipants(participantEntities)

        chat.participants.forEach { participant ->
            chatDao.insertChatParticipantCrossRef(
                ChatParticipantCrossRef(
                    chatId = chat.id.id.toString(),
                    participantId = participant.id.id.toString(),
                ),
            )
        }

        ResultWithError.Success(chat)
    } catch (e: Exception) {
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, LocalDataSourceError> =
        try {
            val chatEntity = chatDao.getChatById(chatId.id.toString())
            if (chatEntity != null) {
                chatDao.deleteChat(chatEntity)
                ResultWithError.Success(Unit)
            } else {
                ResultWithError.Failure(
                    LocalDataSourceError.ChatNotFound,
                )
            }
        } catch (e: Exception) {
            ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
        }

    override fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, LocalDataSourceError>> =
        chatDao.flowAllChatsWithParticipantsAndMessages()
            .map<
                List<ChatWithParticipantsAndMessages>,
                ResultWithError<List<ChatPreview>, LocalDataSourceError>,
                > { chatsWithRelations ->
                val chatPreviews = chatsWithRelations.map { relation ->
                    with(EntityMappers) { relation.toChatPreview() }
                }
                ResultWithError.Success(chatPreviews)
            }
            .catch { e ->
                emit(
                    ResultWithError.Failure<List<ChatPreview>, LocalDataSourceError>(
                        LocalDataSourceError.UnknownError(e),
                    ),
                )
            }

    override fun flowChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, LocalDataSourceError>> =
        chatDao.flowChatWithParticipantsAndMessages(chatId.id.toString())
            .map { relation ->
                if (relation != null) {
                    val chat = with(EntityMappers) {
                        relation.toChat()
                    }
                    ResultWithError.Success(chat)
                } else {
                    ResultWithError.Failure<Chat, LocalDataSourceError>(
                        LocalDataSourceError.ChatNotFound,
                    )
                }
            }
            .catch { e ->
                emit(ResultWithError.Failure(LocalDataSourceError.UnknownError(e)))
            }
}
