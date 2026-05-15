package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import timur.gilfanov.messenger.data.repository.SettingsUserScopedDataCleaner
import timur.gilfanov.messenger.domain.UserScopedDataCleaner

@Module
@InstallIn(SingletonComponent::class)
abstract class UserScopedDataCleanerModule {

    @Binds
    @IntoSet
    abstract fun bindSettingsUserScopedDataCleaner(
        impl: SettingsUserScopedDataCleaner,
    ): UserScopedDataCleaner
}
