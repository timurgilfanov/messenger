package timur.gilfanov.messenger.debug

import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.message.Message

interface SampleDataProvider {
    fun generateChats(config: DataGenerationConfig): List<Chat>
    fun generateSingleMessage(chat: Chat): Message
}
