package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.data.repository.SettingsSyncScheduler
import timur.gilfanov.messenger.data.repository.SettingsSyncSchedulerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsSyncSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindSettingsSyncScheduler(impl: SettingsSyncSchedulerImpl): SettingsSyncScheduler
}
