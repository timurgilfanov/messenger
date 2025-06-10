package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import java.util.UUID
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId

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
}
