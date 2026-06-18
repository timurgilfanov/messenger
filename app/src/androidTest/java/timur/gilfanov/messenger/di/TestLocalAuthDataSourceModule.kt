package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceFake
import timur.gilfanov.messenger.auth.di.AuthDataModule

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthDataModule::class],
)
object TestLocalAuthDataSourceModule {

    @Provides
    @Singleton
    fun provideLocalAuthDataSource(): LocalAuthDataSource = LocalAuthDataSourceFake()
}
