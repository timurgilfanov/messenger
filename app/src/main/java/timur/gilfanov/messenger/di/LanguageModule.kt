package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import timur.gilfanov.messenger.domain.usecase.user.ChangeUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(ViewModelComponent::class)
object LanguageModule {

    @Provides
    fun provideObserveUiLanguageUseCase(
        identityRepository: IdentityRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): ObserveUiLanguageUseCase = ObserveUiLanguageUseCase(
        identityRepository,
        settingsRepository,
        logger,
    )

    @Provides
    fun provideChangeUiLanguageUseCase(
        identityRepository: IdentityRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): ChangeUiLanguageUseCase = ChangeUiLanguageUseCase(
        identityRepository,
        settingsRepository,
        logger,
    )
}
