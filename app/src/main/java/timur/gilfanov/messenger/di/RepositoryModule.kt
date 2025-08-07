package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timur.gilfanov.messenger.data.repository.MessengerRepositoryImpl
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindChatRepository(
        messengerRepositoryImpl: MessengerRepositoryImpl,
    ): ChatRepository

    @Binds
    abstract fun bindMessageRepository(
        messengerRepositoryImpl: MessengerRepositoryImpl,
    ): MessageRepository
}
