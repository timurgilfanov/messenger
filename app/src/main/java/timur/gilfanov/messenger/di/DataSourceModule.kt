package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.local.LocalChatDataSource
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.local.LocalMessageDataSource
import timur.gilfanov.messenger.data.source.local.LocalSyncDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    // Local data source bindings - all interfaces bound to the same implementation
    @Binds
    @Singleton
    abstract fun bindLocalChatDataSource(
        localDataSourceFake: LocalDataSourceFake,
    ): LocalChatDataSource

    @Binds
    @Singleton
    abstract fun bindLocalMessageDataSource(
        localDataSourceFake: LocalDataSourceFake,
    ): LocalMessageDataSource

    @Binds
    @Singleton
    abstract fun bindLocalSyncDataSource(
        localDataSourceFake: LocalDataSourceFake,
    ): LocalSyncDataSource

    // Remote data source bindings - all interfaces bound to the same implementation
    @Binds
    @Singleton
    abstract fun bindRemoteChatDataSource(
        remoteDataSourceFake: RemoteDataSourceFake,
    ): RemoteChatDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteMessageDataSource(
        remoteDataSourceFake: RemoteDataSourceFake,
    ): RemoteMessageDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteSyncDataSource(
        remoteDataSourceFake: RemoteDataSourceFake,
    ): RemoteSyncDataSource
}
