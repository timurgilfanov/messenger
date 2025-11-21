package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.SyncAllPendingSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.user.SyncSettingUseCase
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
object SettingsSyncUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncSettingUseCase(
        identityRepository: IdentityRepository,
        settingsRepository: SettingsRepository,
    ): SyncSettingUseCase = SyncSettingUseCase(identityRepository, settingsRepository)

    @Provides
    @Singleton
    fun provideSyncAllPendingSettingsUseCase(
        identityRepository: IdentityRepository,
        settingsRepository: SettingsRepository,
    ): SyncAllPendingSettingsUseCase = SyncAllPendingSettingsUseCase(
        identityRepository,
        settingsRepository,
    )
}
