package timur.gilfanov.messenger.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.auth.data.repository.AuthRepositoryImpl
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceImpl
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSource
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSourceImpl
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindRemoteAuthDataSource(impl: RemoteAuthDataSourceImpl): RemoteAuthDataSource

    @Binds
    @Singleton
    abstract fun bindLocalAuthDataSource(impl: LocalAuthDataSourceImpl): LocalAuthDataSource
}
