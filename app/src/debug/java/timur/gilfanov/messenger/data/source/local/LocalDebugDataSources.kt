package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId

interface LocalDebugDataSources :
    LocalChatDataSource,
    LocalMessageDataSource,
    LocalSyncDataSource,
    LocalDebugDataSource {
    fun getChat(chatId: ChatId): ResultWithError<Chat, LocalDataSourceError>
}
