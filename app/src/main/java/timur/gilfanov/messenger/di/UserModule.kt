package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.ObserveProfileUseCase
import timur.gilfanov.messenger.domain.usecase.user.ObserveProfileUseCaseImpl
import timur.gilfanov.messenger.domain.usecase.user.ObserveSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.user.ObserveSettingsUseCaseImpl
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Hilt dependency injection module for user-related use cases.
 *
 * Provides use cases scoped to the ViewModel lifecycle for observing
 * user profile and settings data.
 */
@Module
@InstallIn(ViewModelComponent::class)
object UserModule {

    /**
     * Provides [ObserveSettingsUseCase] for observing user settings.
     *
     * @param identityRepository Repository for retrieving current user identity.
     * @param settingsRepository Repository for observing user settings.
     * @param logger Logger for error diagnostics.
     * @return A new instance of [ObserveSettingsUseCase].
     */
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

    /**
     * Provides [ObserveProfileUseCase] for observing user profile.
     *
     * @return A new instance of [ObserveProfileUseCase].
     */
    @Provides
    fun provideObserveProfileUseCase(): ObserveProfileUseCase = ObserveProfileUseCaseImpl()
}
