
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
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
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

    // Remote data source bindings with Named qualifiers for testing
    @Binds
    @Singleton
    @Named("fake")
    abstract fun bindFakeRemoteChatDataSource(
        remoteDataSourceFake: RemoteDataSourceFake,
    ): RemoteChatDataSource

    @Binds
    @Singleton
    @Named("fake")
    abstract fun bindFakeRemoteMessageDataSource(
        remoteDataSourceFake: RemoteDataSourceFake,
    ): RemoteMessageDataSource

    @Binds
    @Singleton
    @Named("fake")
    abstract fun bindFakeRemoteSyncDataSource(
        remoteDataSourceFake: RemoteDataSourceFake,
    ): RemoteSyncDataSource

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

/**
 * Provides the actual RemoteDataSource implementations based on feature flag.
 * This allows runtime switching between fake and real implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteDataSourceProviders {
    @Provides
    @Singleton
    fun provideRemoteChatDataSource(
        @Named("fake") fakeDataSource: RemoteChatDataSource,
        @Named("real") realDataSource: RemoteChatDataSource,
        @Named("useRealRemoteDataSources") useReal: Boolean,
    ): RemoteChatDataSource = if (useReal) realDataSource else fakeDataSource

    @Provides
    @Singleton
    fun provideRemoteMessageDataSource(
        @Named("fake") fakeDataSource: RemoteMessageDataSource,
        @Named("real") realDataSource: RemoteMessageDataSource,
        @Named("useRealRemoteDataSources") useReal: Boolean,
    ): RemoteMessageDataSource = if (useReal) realDataSource else fakeDataSource

    @Provides
    @Singleton
    fun provideRemoteSyncDataSource(
        @Named("fake") fakeDataSource: RemoteSyncDataSource,
        @Named("real") realDataSource: RemoteSyncDataSource,
        @Named("useRealRemoteDataSources") useReal: Boolean,
    ): RemoteSyncDataSource = if (useReal) realDataSource else fakeDataSource
}
