package timur.gilfanov.messenger.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSourceImpl
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource
import timur.gilfanov.messenger.debug.DebugDataRepository
import timur.gilfanov.messenger.debug.DebugDataRepositoryImpl
import timur.gilfanov.messenger.debug.SampleDataProvider
import timur.gilfanov.messenger.debug.SampleDataProviderImpl
import timur.gilfanov.messenger.util.Logger

/**
 * Debug-only DI module for fake data sources.
 * This module is only available in debug builds and provides fake implementations
 * for development and testing purposes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugDataSourceModule {

    // Fake remote data source bindings
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

    // Debug-specific interface bindings
    @Binds
    @Singleton
    abstract fun bindRemoteDebugDataSource(
        remoteDataSourceFake: RemoteDataSourceFake,
    ): RemoteDebugDataSource

    @Binds
    @Singleton
    abstract fun bindDebugDataRepository(
        debugDataRepositoryImpl: DebugDataRepositoryImpl,
    ): DebugDataRepository

    @Binds
    @Singleton
    abstract fun bindSampleDataProvider(
        sampleDataProviderImpl: SampleDataProviderImpl,
    ): SampleDataProvider

    companion object {
        @Provides
        @Singleton
        fun provideLocalDebugDataSource(
            database: MessengerDatabase,
            @Named("debug") dataStore: DataStore<Preferences>,
            logger: Logger,
        ): LocalDebugDataSource = LocalDebugDataSourceImpl(
            database = database,
            dataStore = dataStore,
            logger = logger,
        )
    }
}

/**
 * Debug-only providers that allow runtime switching between fake and real implementations.
 * This module is only available in debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugRemoteDataSourceProviders {
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
