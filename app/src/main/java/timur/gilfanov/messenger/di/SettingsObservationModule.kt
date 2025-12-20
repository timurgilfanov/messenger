package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCaseImpl
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(ViewModelComponent::class)
object SettingsObservationModule {

    @Provides
    fun provideObserveSettingsUseCase(
        identityRepository: IdentityRepository,
        settingsRepository: SettingsRepository,
        logger: Logger,
    ): ObserveSettingsUseCase = ObserveSettingsUseCaseImpl(
        identityRepository,
        settingsRepository,
        logger,
    )
}
