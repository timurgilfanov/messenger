package timur.gilfanov.messenger.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import timur.gilfanov.messenger.auth.AuthInterceptor
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshUseCase
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshUseCaseImpl
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidator
import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.auth.ui.GoogleSignInClient
import timur.gilfanov.messenger.auth.ui.GoogleSignInClientImpl
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideTokenRefreshUseCase(
        authRepository: AuthRepository,
        logger: Logger,
    ): TokenRefreshUseCase = TokenRefreshUseCaseImpl(authRepository, logger)

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        authSessionStorage: LocalAuthDataSource,
        tokenRefreshUseCase: TokenRefreshUseCase,
        scope: CoroutineScope,
    ): AuthInterceptor = AuthInterceptor(authSessionStorage, tokenRefreshUseCase, scope)

    @Provides
    @Singleton
    fun provideCredentialsValidator(): CredentialsValidator = CredentialsValidatorImpl()

    @Provides
    @Singleton
    fun provideGoogleSignInClient(logger: Logger): GoogleSignInClient =
        GoogleSignInClientImpl(logger)
}
