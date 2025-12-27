package timur.gilfanov.messenger.test

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.repository.DeleteMessageRepositoryError
import timur.gilfanov.messenger.domain.usecase.message.repository.EditMessageRepositoryError
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError

class MessageRepositoryStub : MessageRepository {

    override suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, SendMessageRepositoryError>> = throw NotImplementedError()

    override suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, EditMessageRepositoryError>> = throw NotImplementedError()

    override suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, DeleteMessageRepositoryError> = throw NotImplementedError()

    override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> = emptyFlow()
}
