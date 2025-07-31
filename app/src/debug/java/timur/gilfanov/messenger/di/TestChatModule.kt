package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.data.repository.ALICE_CHAT_ID

@Module
@InstallIn(SingletonComponent::class)
object TestChatModule {

    @Provides
    @Singleton
    @TestChatId
    fun provideTestChatId(): String = ALICE_CHAT_ID
}
