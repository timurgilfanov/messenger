package timur.gilfanov.messenger.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.auth.data.repository.AuthCleanupObserver
import timur.gilfanov.messenger.auth.data.repository.AuthCleanupObserverImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthCleanupObserverModule {

    @Binds
    @Singleton
    abstract fun bindAuthCleanupObserver(impl: AuthCleanupObserverImpl): AuthCleanupObserver
}
