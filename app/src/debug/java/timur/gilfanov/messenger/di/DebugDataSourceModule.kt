package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.collections.immutable.persistentMapOf
import timur.gilfanov.messenger.data.source.remote.RemoteChatDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteMessageDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteSyncDataSource

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

    @Binds
    @Singleton
    @Named("fake")
    abstract fun bindFakeRemoteSettingsDataSource(
        remoteSettingsDataSourceFake: RemoteSettingsDataSourceFake,
    ): RemoteSettingsDataSource
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

    @Provides
    @Singleton
    fun provideRemoteSettingsDataSourceFake(): RemoteSettingsDataSourceFake =
        RemoteSettingsDataSourceFake(persistentMapOf())

    @Provides
    @Singleton
    fun provideRemoteSettingsDataSource(
        @Named("fake") fakeDataSource: RemoteSettingsDataSource,
        @Named("real") realDataSource: RemoteSettingsDataSource,
        @Named("useRealRemoteDataSources") useReal: Boolean,
    ): RemoteSettingsDataSource = if (useReal) realDataSource else fakeDataSource
}
