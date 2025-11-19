package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.usecase.user.SyncAllPendingSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.user.SyncSettingUseCase
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
object SettingsSyncUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncSettingUseCase(settingsRepository: SettingsRepository): SyncSettingUseCase =
        SyncSettingUseCase(settingsRepository)

    @Provides
    @Singleton
    fun provideSyncAllPendingSettingsUseCase(
        settingsRepository: SettingsRepository,
    ): SyncAllPendingSettingsUseCase = SyncAllPendingSettingsUseCase(settingsRepository)
}
