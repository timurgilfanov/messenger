package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.data.repository.USER_ID

@Module
@InstallIn(SingletonComponent::class)
object TestUserModule {

    @Provides
    @Singleton
    @TestUserId
    fun provideTestUserId(): String = USER_ID
}
