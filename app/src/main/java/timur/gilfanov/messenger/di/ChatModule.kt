package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListUseCase
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase

@Module
@InstallIn(ViewModelComponent::class)
object ChatModule {

    @Provides
    @ViewModelScoped
    fun provideDeliveryStatusValidator(): DeliveryStatusValidator = DeliveryStatusValidatorImpl()

    @Provides
    @ViewModelScoped
    fun provideSendMessageUseCase(
        repository: MessageRepository,
        deliveryStatusValidator: DeliveryStatusValidator,
    ): SendMessageUseCase = SendMessageUseCase(repository, deliveryStatusValidator)

    @Provides
    @ViewModelScoped
    fun provideReceiveChatUpdatesUseCase(repository: ChatRepository): ReceiveChatUpdatesUseCase =
        ReceiveChatUpdatesUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideFlowChatListUseCase(repository: ChatRepository): FlowChatListUseCase =
        FlowChatListUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideGetPagedMessagesUseCase(repository: MessageRepository): GetPagedMessagesUseCase =
        GetPagedMessagesUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideMarkMessagesAsReadUseCase(repository: ChatRepository): MarkMessagesAsReadUseCase =
        MarkMessagesAsReadUseCase(repository)
}
