package timur.gilfanov.messenger.debug

import kotlinx.collections.immutable.ImmutableList
import timur.gilfanov.messenger.data.source.remote.AddChatError
import timur.gilfanov.messenger.data.source.remote.AddMessageError
import timur.gilfanov.messenger.data.source.remote.GetChatsError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.message.Message

class RemoteDebugDataSourceFakeDecorator(private val source: RemoteDebugDataSourceFake) :
    RemoteDebugDataSourceFake by source {

    var remoteAddChatError: AddChatError? = null
    var remoteAddMessageError: AddMessageError? = null
    var remoteGetChatsError: GetChatsError? = null

    override fun addChat(chat: Chat): ResultWithError<Unit, AddChatError> {
        remoteAddChatError?.let { error ->
            return ResultWithError.Failure(error)
        }
        return source.addChat(chat)
    }

    override fun addMessage(message: Message): ResultWithError<Unit, AddMessageError> {
        remoteAddMessageError?.let { error ->
            return ResultWithError.Failure(error)
        }
        return source.addMessage(message)
    }

    override fun getChats(): ResultWithError<ImmutableList<Chat>, GetChatsError> {
        remoteGetChatsError?.let { error ->
            return ResultWithError.Failure(error)
        }
        return source.getChats()
    }
}
