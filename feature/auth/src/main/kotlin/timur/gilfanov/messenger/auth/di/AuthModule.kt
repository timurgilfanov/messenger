package timur.gilfanov.messenger.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.auth.login.LoginWithCredentialsUseCase
import timur.gilfanov.messenger.auth.login.LoginWithCredentialsUseCaseImpl
import timur.gilfanov.messenger.auth.login.LoginWithGoogleUseCase
import timur.gilfanov.messenger.auth.login.LoginWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState.Authenticated
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = AuthRepositoryFake(
        initialAuthState = Authenticated(
            AuthSession(AuthTokens("stub-access", "stub-refresh"), AuthProvider.EMAIL),
        ),
    )

    @Provides
    @Singleton
    fun provideCredentialsValidator(): CredentialsValidator = CredentialsValidatorImpl()

    @Provides
    @Singleton
    fun provideLoginWithCredentialsUseCase(
        validator: CredentialsValidator,
        repository: AuthRepository,
        logger: Logger,
    ): LoginWithCredentialsUseCase = LoginWithCredentialsUseCaseImpl(validator, repository, logger)

    @Provides
    @Singleton
    fun provideLoginWithGoogleUseCase(
        repository: AuthRepository,
        logger: Logger,
    ): LoginWithGoogleUseCase = LoginWithGoogleUseCaseImpl(repository, logger)
}
