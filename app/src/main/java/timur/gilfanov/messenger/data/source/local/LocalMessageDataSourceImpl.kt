package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteException
import androidx.paging.PagingSource
import androidx.room.withTransaction
import javax.inject.Inject
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.data.source.paging.MessagePagingSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.util.Logger

class LocalMessageDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    logger: Logger,
) : LocalMessageDataSource {

    private val errorHandler = DatabaseErrorHandler(logger)

    override suspend fun insertMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> {
        val validationError = validateMessageForInsert(message)
        if (validationError != null) {
            return ResultWithError.Failure(validationError)
        }

        return try {
            database.withTransaction {
                val messageEntity = with(EntityMappers) { message.toMessageEntity() }
                messageDao.insertMessage(messageEntity)
            }
            ResultWithError.Success(message)
        } catch (e: SQLiteException) {
            ResultWithError.Failure(errorHandler.mapException(e))
        }
    }

    override suspend fun updateMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> {
        val validationError = validateMessageForUpdate(message)
        if (validationError != null) {
            return ResultWithError.Failure(validationError)
        }

        return try {
            database.withTransaction {
                val messageEntity = with(EntityMappers) { message.toMessageEntity() }
                messageDao.updateMessage(messageEntity)
            }
            ResultWithError.Success(message)
        } catch (e: SQLiteException) {
            ResultWithError.Failure(errorHandler.mapException(e))
        }
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
    ): ResultWithError<Unit, LocalDataSourceError> = try {
        database.withTransaction {
            val messageEntity = messageDao.getMessageById(messageId.id.toString())
            if (messageEntity != null) {
                messageDao.deleteMessage(messageEntity)
                ResultWithError.Success(Unit)
            } else {
                ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
            }
        }
    } catch (e: SQLiteException) {
        ResultWithError.Failure(errorHandler.mapException(e))
    }

    override suspend fun getMessage(
        messageId: MessageId,
    ): ResultWithError<Message, LocalDataSourceError> = try {
        val messageEntity = messageDao.getMessageById(messageId.id.toString())
        if (messageEntity != null) {
            val chat = chatDao.getChatWithParticipantsAndMessages(messageEntity.chatId)
            val participants = chat?.participants ?: emptyList()
            val participantCrossRefs = chat?.participantCrossRefs ?: emptyList()
            val message = with(EntityMappers) {
                messageEntity.toMessage(participants, participantCrossRefs)
            }
            ResultWithError.Success(message)
        } else {
            ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
        }
    } catch (e: SQLiteException) {
        ResultWithError.Failure(errorHandler.mapException(e))
    }

    private fun validateMessageForInsert(message: Message): LocalDataSourceError? {
        val textError = when (message) {
            is TextMessage -> {
                when {
                    message.text.isBlank() ->
                        LocalDataSourceError.InvalidData("text", "Message text cannot be blank")
                    message.text.length > TextMessage.MAX_TEXT_LENGTH ->
                        LocalDataSourceError.InvalidData(
                            "text",
                            "Message text cannot exceed ${TextMessage.MAX_TEXT_LENGTH} characters",
                        )
                    else -> null
                }
            }

            else -> {
                null
            }
        }

        if (textError != null) return textError

        val sentAt = message.sentAt
        val deliveredAt = message.deliveredAt

        return when {
            sentAt != null && message.createdAt > sentAt ->
                LocalDataSourceError.InvalidData(
                    "timestamps",
                    "Created time cannot be after sent time",
                )
            deliveredAt != null && sentAt != null && sentAt > deliveredAt ->
                LocalDataSourceError.InvalidData(
                    "timestamps",
                    "Sent time cannot be after delivered time",
                )
            else -> null
        }
    }

    private fun validateMessageForUpdate(message: Message): LocalDataSourceError? =
        validateMessageForInsert(message)

    override fun getMessagePagingSource(chatId: ChatId): PagingSource<Long, Message> =
        MessagePagingSource(
            messageDao = messageDao,
            chatDao = chatDao,
            chatId = chatId,
        )
}
