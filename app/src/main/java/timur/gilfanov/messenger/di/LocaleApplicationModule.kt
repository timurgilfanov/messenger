package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.ObserveAndApplyLocaleUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(SingletonComponent::class)
object LocaleApplicationModule {

    @Provides
    @Singleton
    fun provideObserveAndApplyLocaleUseCase(
        authRepository: AuthRepository,
        settingsRepository: SettingsRepository,
        localeRepository: LocaleRepository,
        logger: Logger,
    ): ObserveAndApplyLocaleUseCase = ObserveAndApplyLocaleUseCase(
        observeUiLanguage = ObserveUiLanguageUseCase(
            authRepository,
            settingsRepository,
            logger,
        ),
        localeRepository = localeRepository,
        logger = logger,
    )
}
