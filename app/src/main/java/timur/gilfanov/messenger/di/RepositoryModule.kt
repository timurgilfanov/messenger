package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timur.gilfanov.messenger.data.repository.MessengerRepositoryImpl
import timur.gilfanov.messenger.data.repository.ParticipantRepositoryImpl
import timur.gilfanov.messenger.data.repository.PrivilegedRepositoryImpl
import timur.gilfanov.messenger.domain.usecase.ChatRepository
import timur.gilfanov.messenger.domain.usecase.MessageRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.privileged.PrivilegedRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindParticipantRepository(
        participantRepositoryImpl: ParticipantRepositoryImpl,
    ): ParticipantRepository

    @Binds
    abstract fun bindPrivilegedRepository(
        privilegedRepositoryImpl: PrivilegedRepositoryImpl,
    ): PrivilegedRepository

    @Binds
    abstract fun bindChatRepository(
        messengerRepositoryImpl: MessengerRepositoryImpl,
    ): ChatRepository

    @Binds
    abstract fun bindMessageRepository(
        messengerRepositoryImpl: MessengerRepositoryImpl,
    ): MessageRepository
}
