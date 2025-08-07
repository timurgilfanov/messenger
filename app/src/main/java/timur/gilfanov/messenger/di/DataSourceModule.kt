package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timur.gilfanov.messenger.data.source.local.LocalDataSource
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    abstract fun bindLocalDataSource(localDataSourceFake: LocalDataSourceFake): LocalDataSource

    @Binds
    abstract fun bindRemoteDataSource(remoteDataSourceFake: RemoteDataSourceFake): RemoteDataSource
}
