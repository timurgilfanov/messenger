package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource

/**
 * Release-only DI module that provides remote data sources directly.
 * In release builds, we only use real implementations without runtime switching.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReleaseDataSourceModule {
    @Provides
    @Singleton
    fun provideRemoteChatDataSource(
        @Named("real") realDataSource: RemoteChatDataSource,
    ): RemoteChatDataSource = realDataSource

    @Provides
    @Singleton
    fun provideRemoteMessageDataSource(
        @Named("real") realDataSource: RemoteMessageDataSource,
    ): RemoteMessageDataSource = realDataSource

    @Provides
    @Singleton
    fun provideRemoteSyncDataSource(
        @Named("real") realDataSource: RemoteSyncDataSource,
    ): RemoteSyncDataSource = realDataSource
}
