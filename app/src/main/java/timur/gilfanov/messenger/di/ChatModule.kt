package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListUseCase
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase

@Module
@InstallIn(ViewModelComponent::class)
object ChatModule {

    @Provides
    @ViewModelScoped
    fun provideDeliveryStatusValidator(): DeliveryStatusValidator = DeliveryStatusValidatorImpl()

    @Provides
    @ViewModelScoped
    fun provideSendMessageUseCase(
        repository: ParticipantRepository,
        deliveryStatusValidator: DeliveryStatusValidator,
    ): SendMessageUseCase = SendMessageUseCase(repository, deliveryStatusValidator)

    @Provides
    @ViewModelScoped
    fun provideReceiveChatUpdatesUseCase(
        repository: ParticipantRepository,
    ): ReceiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideFlowChatListUseCase(repository: ParticipantRepository): FlowChatListUseCase =
        FlowChatListUseCase(repository)
}
