package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.ChangeUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(ViewModelComponent::class)
object LanguageModule {

    @Provides
    fun provideObserveUiLanguageUseCase(
        authRepository: AuthRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): ObserveUiLanguageUseCase = ObserveUiLanguageUseCase(
        authRepository,
        settingsRepository,
        logger,
    )

    @Provides
    fun provideChangeUiLanguageUseCase(
        authRepository: AuthRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): ChangeUiLanguageUseCase = ChangeUiLanguageUseCase(
        authRepository,
        settingsRepository,
        logger,
    )
}
