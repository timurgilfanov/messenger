package timur.gilfanov.messenger.data.source.local

import androidx.room.withTransaction
import javax.inject.Inject
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

class LocalMessageDataSourceImpl @Inject constructor(
    private val database: MessengerDatabase,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
) : LocalMessageDataSource {

    @Suppress("TooGenericExceptionCaught") // DatabaseErrorHandler maps all exception types
    override suspend fun insertMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> {
        // Validation
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
        } catch (e: Exception) {
            ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
        }
    }

    @Suppress("TooGenericExceptionCaught") // DatabaseErrorHandler maps all exception types
    override suspend fun updateMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> {
        // Validation
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
        } catch (e: Exception) {
            ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
        }
    }

    @Suppress("TooGenericExceptionCaught") // DatabaseErrorHandler maps all exception types
    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
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
    } catch (e: Exception) {
        ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
    }

    @Suppress("TooGenericExceptionCaught") // DatabaseErrorHandler maps all exception types
    override suspend fun getMessage(
        messageId: MessageId,
    ): ResultWithError<Message, LocalDataSourceError> = try {
        val messageEntity = messageDao.getMessageById(messageId.id.toString())
        if (messageEntity != null) {
            val chat = chatDao.getChatWithParticipantsAndMessages(messageEntity.chatId)
            val participants = chat?.participants ?: emptyList()
            val message = with(EntityMappers) { messageEntity.toMessage(participants) }
            ResultWithError.Success(message)
        } else {
            ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
        }
    } catch (e: Exception) {
        ResultWithError.Failure(DatabaseErrorHandler.mapException(e))
    }

    private fun validateMessageForInsert(message: Message): LocalDataSourceError? {
        // Check if it's a TextMessage for text-specific validation
        val textError = if (message is TextMessage) {
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
        } else {
            null
        }

        if (textError != null) return textError

        // General message validation with proper null handling
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

    private fun validateMessageForUpdate(message: Message): LocalDataSourceError? {
        // Similar validation for updates
        return validateMessageForInsert(message)
    }
}
