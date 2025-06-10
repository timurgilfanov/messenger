package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import java.util.UUID
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase

@Module
@InstallIn(ViewModelComponent::class)
object ChatModule {

    @Provides
    @ViewModelScoped
    fun provideChatId(): ChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

    @Provides
    @ViewModelScoped
    fun provideCurrentUserId(): ParticipantId =
        ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))

    @Provides
    @ViewModelScoped
    fun provideDeliveryStatusValidator(): DeliveryStatusValidator = DeliveryStatusValidatorImpl()

    @Provides
    @ViewModelScoped
    fun provideSendMessageUseCase(
        repository: ParticipantRepository,
        deliveryStatusValidator: DeliveryStatusValidator
    ): SendMessageUseCase = SendMessageUseCase(repository, deliveryStatusValidator)

    @Provides
    @ViewModelScoped
    fun provideReceiveChatUpdatesUseCase(
        repository: ParticipantRepository
    ): ReceiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)
}
