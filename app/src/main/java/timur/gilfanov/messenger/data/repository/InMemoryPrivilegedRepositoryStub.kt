package timur.gilfanov.messenger.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.privileged.PrivilegedRepository
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryDeleteChatError

@Singleton
class InMemoryPrivilegedRepositoryStub @Inject constructor() : PrivilegedRepository {

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError> =
        ResultWithError.Success(chat)

    override suspend fun deleteChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryDeleteChatError> = ResultWithError.Success(Unit)
}
