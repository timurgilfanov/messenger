
package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import timur.gilfanov.messenger.BuildConfig
import timur.gilfanov.messenger.data.source.local.LocalChatDataSource
import timur.gilfanov.messenger.data.source.local.LocalChatDataSourceImpl
import timur.gilfanov.messenger.data.source.local.LocalMessageDataSource
import timur.gilfanov.messenger.data.source.local.LocalMessageDataSourceImpl
import timur.gilfanov.messenger.data.source.local.LocalSyncDataSource
import timur.gilfanov.messenger.data.source.local.LocalSyncDataSourceImpl
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSourceImpl
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSourceImpl
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSourceImpl

/**
 * Feature flag for using real remote data sources.
 * Can be configured through BuildConfig or remote configuration.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataSourceFeatureFlags {
    @Suppress("KotlinConstantConditions")
    @Provides
    @Singleton
    @Named("useRealRemoteDataSources")
    fun provideUseRealRemoteDataSources(): Boolean = BuildConfig.USE_REAL_REMOTE_DATA_SOURCES
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    // Local data source bindings
    @Binds
    @Singleton
    abstract fun bindLocalChatDataSource(
        localChatDataSourceImpl: LocalChatDataSourceImpl,
    ): LocalChatDataSource

    @Binds
    @Singleton
    abstract fun bindLocalMessageDataSource(
        localMessageDataSourceImpl: LocalMessageDataSourceImpl,
    ): LocalMessageDataSource

    @Binds
    @Singleton
    abstract fun bindLocalSyncDataSource(
        localSyncDataSourceImpl: LocalSyncDataSourceImpl,
    ): LocalSyncDataSource

    // Real remote data source bindings
    @Binds
    @Singleton
    @Named("real")
    abstract fun bindRealRemoteChatDataSource(
        remoteChatDataSourceImpl: RemoteChatDataSourceImpl,
    ): RemoteChatDataSource

    @Binds
    @Singleton
    @Named("real")
    abstract fun bindRealRemoteMessageDataSource(
        remoteMessageDataSourceImpl: RemoteMessageDataSourceImpl,
    ): RemoteMessageDataSource

    @Binds
    @Singleton
    @Named("real")
    abstract fun bindRealRemoteSyncDataSource(
        remoteSyncDataSourceImpl: RemoteSyncDataSourceImpl,
    ): RemoteSyncDataSource
}
