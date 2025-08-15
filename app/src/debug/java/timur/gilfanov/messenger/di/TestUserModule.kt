package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TestUserModule {

    @Suppress("FunctionOnlyReturningConstant") // value provided only for compilation
    @Provides
    @Singleton
    @TestUserId
    fun provideTestUserId(): String = "00000000-0000-0000-0000-000000000000"
}
