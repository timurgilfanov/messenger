package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.SyncAllPendingSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.settings.SyncSettingUseCase
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(SingletonComponent::class)
object SettingsSyncUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncSettingUseCase(
        identityRepository: IdentityRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): SyncSettingUseCase = SyncSettingUseCase(identityRepository, settingsRepository, logger)

    @Provides
    @Singleton
    fun provideSyncAllPendingSettingsUseCase(
        identityRepository: IdentityRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): SyncAllPendingSettingsUseCase = SyncAllPendingSettingsUseCase(
        identityRepository,
        settingsRepository,
        logger,
    )
}
