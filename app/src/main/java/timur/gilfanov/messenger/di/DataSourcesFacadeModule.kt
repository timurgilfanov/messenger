package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.local.LocalChatDataSource
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.local.LocalMessageDataSource
import timur.gilfanov.messenger.data.source.local.LocalSyncDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSources
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource

@Module
@InstallIn(SingletonComponent::class)
object DataSourcesFacadeModule {

    @Provides
    @Singleton
    fun provideLocalDataSources(
        chatDataSource: LocalChatDataSource,
        messageDataSource: LocalMessageDataSource,
        syncDataSource: LocalSyncDataSource,
    ): LocalDataSources = LocalDataSources(
        chat = chatDataSource,
        message = messageDataSource,
        sync = syncDataSource,
    )

    @Provides
    @Singleton
    fun provideRemoteDataSources(
        chatDataSource: RemoteChatDataSource,
        messageDataSource: RemoteMessageDataSource,
        syncDataSource: RemoteSyncDataSource,
    ): RemoteDataSources = RemoteDataSources(
        chat = chatDataSource,
        message = messageDataSource,
        sync = syncDataSource,
    )
}
