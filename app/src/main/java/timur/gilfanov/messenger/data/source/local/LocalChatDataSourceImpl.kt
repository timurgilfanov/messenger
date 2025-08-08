package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.ChatWithParticipantsAndMessages
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

class LocalChatDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val chatDao: ChatDao,
    private val participantDao: ParticipantDao,
) : LocalChatDataSource {

    override suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> {
        val validationError = validateChatForInsert(chat)
        if (validationError != null) {
            return ResultWithError.Failure(validationError)
        }

        return try {
            database.withTransaction {
                val chatEntity = with(EntityMappers) { chat.toChatEntity() }
                val participantEntities = chat.participants.map { participant ->
                    with(EntityMappers) { participant.toParticipantEntity() }
                }

                chatDao.insertChat(chatEntity)

                participantDao.insertParticipants(participantEntities)

                chat.participants.forEach { participant ->
                    chatDao.insertChatParticipantCrossRef(
                        ChatParticipantCrossRef(
                            chatId = chat.id.id.toString(),
                            participantId = participant.id.id.toString(),
                        ),
                    )
                }
            }

            ResultWithError.Success(chat)
        } catch (e: SQLiteException) {
            ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
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
            }

            ResultWithError.Success(chat)
        } catch (e: SQLiteException) {
            ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
        }
    }

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, LocalDataSourceError> =
        try {
            database.withTransaction {
                val chatEntity = chatDao.getChatById(chatId.id.toString())
                if (chatEntity != null) {
                    chatDao.deleteChat(chatEntity)
                    ResultWithError.Success(Unit)
                } else {
                    ResultWithError.Failure(
                        LocalDataSourceError.ChatNotFound,
                    )
                }
            }
        } catch (e: SQLiteException) {
            ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
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
                when (e) {
                    is SQLiteException -> emit(
                        ResultWithError.Failure(DatabaseErrorHandler.mapException(e)),
                    )
                    else -> throw e
                }
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
                when (e) {
                    is SQLiteException -> emit(
                        ResultWithError.Failure(DatabaseErrorHandler.mapException(e)),
                    )
                    else -> throw e
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
