package timur.gilfanov.messenger.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import timur.gilfanov.messenger.auth.AuthInterceptor
import timur.gilfanov.messenger.auth.AuthTokenStorage
import timur.gilfanov.messenger.auth.TokenRefreshError
import timur.gilfanov.messenger.auth.TokenRefreshUseCase
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState.Authenticated
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthTokenStorage(): AuthTokenStorage = object : AuthTokenStorage {
        override suspend fun getAccessToken(): String? = null
        override suspend fun getRefreshToken(): String? = null
        override suspend fun saveTokens(tokens: AuthTokens) = Unit
        override suspend fun clearTokens() = Unit
    }

    @Provides
    @Singleton
    fun provideTokenRefreshUseCase(): TokenRefreshUseCase =
        TokenRefreshUseCase { ResultWithError.Failure(TokenRefreshError.SessionExpired) }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        authTokenStorage: AuthTokenStorage,
        tokenRefreshUseCase: TokenRefreshUseCase,
        scope: CoroutineScope,
    ): AuthInterceptor = AuthInterceptor(authTokenStorage, tokenRefreshUseCase, scope)

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = AuthRepositoryFake(
        initialAuthState = Authenticated(
            AuthSession(AuthTokens("stub-access", "stub-refresh"), AuthProvider.EMAIL),
        ),
    )
}
