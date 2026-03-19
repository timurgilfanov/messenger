package timur.gilfanov.messenger.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import timur.gilfanov.messenger.auth.AuthInterceptor
import timur.gilfanov.messenger.auth.TokenRefreshError
import timur.gilfanov.messenger.auth.TokenRefreshUseCase
import timur.gilfanov.messenger.auth.data.storage.AuthSessionStorage
import timur.gilfanov.messenger.auth.login.GoogleSignInClient
import timur.gilfanov.messenger.auth.login.GoogleSignInClientImpl
import timur.gilfanov.messenger.auth.login.LoginWithCredentialsUseCase
import timur.gilfanov.messenger.auth.login.LoginWithCredentialsUseCaseImpl
import timur.gilfanov.messenger.auth.login.LoginWithGoogleUseCase
import timur.gilfanov.messenger.auth.login.LoginWithGoogleUseCaseImpl
import timur.gilfanov.messenger.auth.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidator
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideTokenRefreshUseCase(): TokenRefreshUseCase =
        TokenRefreshUseCase { ResultWithError.Failure(TokenRefreshError.SessionExpired) }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        authSessionStorage: AuthSessionStorage,
        tokenRefreshUseCase: TokenRefreshUseCase,
        scope: CoroutineScope,
    ): AuthInterceptor = AuthInterceptor(authSessionStorage, tokenRefreshUseCase, scope)

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

    @Provides
    @Singleton
    fun provideGoogleSignInClient(logger: Logger): GoogleSignInClient =
        GoogleSignInClientImpl(logger)
}
