package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
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
        authRepository: AuthRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): SyncSettingUseCase = SyncSettingUseCase(authRepository, settingsRepository, logger)

    @Provides
    @Singleton
    fun provideSyncAllPendingSettingsUseCase(
        authRepository: AuthRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): SyncAllPendingSettingsUseCase = SyncAllPendingSettingsUseCase(
        authRepository,
        settingsRepository,
        logger,
    )
}
