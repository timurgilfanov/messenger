package timur.gilfanov.messenger.domain.usecase

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.message.Message

interface Repository {
    suspend fun sendMessage(message: Message): Flow<Message>
}
