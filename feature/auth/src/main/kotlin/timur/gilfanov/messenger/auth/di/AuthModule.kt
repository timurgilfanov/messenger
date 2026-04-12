package timur.gilfanov.messenger.auth.di

import dagger.Lazy
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
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideTokenRefreshUseCase(
        authRepository: AuthRepository,
        settingsRepositoryLazy: Lazy<SettingsRepository>,
        logger: Logger,
    ): TokenRefreshUseCase {
        // Break the DI cycle:
        //   TokenRefreshUseCase -> SettingsRepository -> RemoteSettingsDataSource
        //   -> HttpClient -> AuthInterceptor -> TokenRefreshUseCase
        // Dagger.Lazy defers SettingsRepository instantiation until first use, after
        // all singletons in the cycle are already constructed.
        val settingsRepository = object : SettingsRepository {
            override fun observeSettings(userKey: UserScopeKey) =
                settingsRepositoryLazy.get().observeSettings(userKey)

            override fun observeConflicts() = settingsRepositoryLazy.get().observeConflicts()

            override suspend fun changeUiLanguage(userKey: UserScopeKey, language: UiLanguage) =
                settingsRepositoryLazy.get().changeUiLanguage(userKey, language)

            override suspend fun syncSetting(userKey: UserScopeKey, key: SettingKey) =
                settingsRepositoryLazy.get().syncSetting(userKey, key)

            override suspend fun syncAllPendingSettings(userKey: UserScopeKey) =
                settingsRepositoryLazy.get().syncAllPendingSettings(userKey)

            override suspend fun deleteUserData(userKey: UserScopeKey) =
                settingsRepositoryLazy.get().deleteUserData(userKey)
        }
        return TokenRefreshUseCaseImpl(authRepository, settingsRepository, logger)
    }

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
