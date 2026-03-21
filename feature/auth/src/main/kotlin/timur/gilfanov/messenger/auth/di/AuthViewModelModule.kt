package timur.gilfanov.messenger.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithCredentialsUseCase
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithCredentialsUseCaseImpl
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithGoogleUseCase
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.domain.usecase.LogoutUseCase
import timur.gilfanov.messenger.auth.domain.usecase.LogoutUseCaseImpl
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(ViewModelComponent::class)
object AuthViewModelModule {

    @Provides
    fun provideLoginWithCredentialsUseCase(
        validator: CredentialsValidator,
        repository: AuthRepository,
        logger: Logger,
    ): LoginWithCredentialsUseCase = LoginWithCredentialsUseCaseImpl(validator, repository, logger)

    @Provides
    fun provideLoginWithGoogleUseCase(
        repository: AuthRepository,
        logger: Logger,
    ): LoginWithGoogleUseCase = LoginWithGoogleUseCaseImpl(repository, logger)

    @Provides
    fun provideLogoutUseCase(authRepository: AuthRepository, logger: Logger): LogoutUseCase =
        LogoutUseCaseImpl(authRepository, logger)
}
