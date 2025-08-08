package timur.gilfanov.messenger.data.source.local

import javax.inject.Inject
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

@Suppress("TooGenericExceptionCaught")
class LocalMessageDataSourceImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
) : LocalMessageDataSource {

    override suspend fun insertMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> = try {
        val messageEntity = with(EntityMappers) { message.toMessageEntity() }
        messageDao.insertMessage(messageEntity)
        ResultWithError.Success(message)
    } catch (e: Exception) {
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }

    override suspend fun updateMessage(
        message: Message,
    ): ResultWithError<Message, LocalDataSourceError> = try {
        val messageEntity = with(EntityMappers) { message.toMessageEntity() }
        messageDao.updateMessage(messageEntity)
        ResultWithError.Success(message)
    } catch (e: Exception) {
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, LocalDataSourceError> = try {
        val messageEntity = messageDao.getMessageById(messageId.id.toString())
        if (messageEntity != null) {
            messageDao.deleteMessage(messageEntity)
            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(LocalDataSourceError.MessageNotFound)
        }
    } catch (e: Exception) {
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }

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
        ResultWithError.Failure(LocalDataSourceError.UnknownError(e))
    }
}
