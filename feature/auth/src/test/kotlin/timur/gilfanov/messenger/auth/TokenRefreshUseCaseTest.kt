package timur.gilfanov.messenger.auth

import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshError
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshUseCaseImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryFake
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class TokenRefreshUseCaseTest {

    private val initialSession = AuthSession(
        tokens = AuthTokens(
            accessToken = "initial-access-token",
            refreshToken = "initial-refresh-token",
        ),
        provider = AuthProvider.EMAIL,
    )
    private val newTokens = AuthTokens(
        accessToken = "new-access-token",
        refreshToken = "new-refresh-token",
    )

    private fun createUseCase(
        authRepository: AuthRepositoryFake = AuthRepositoryFake(initialSession),
        settingsRepository:
        timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository =
            SettingsRepositoryStub(),
    ) = TokenRefreshUseCaseImpl(authRepository, settingsRepository, NoOpLogger())

    @Test
    fun `when refresh succeeds then returns new tokens and state remains authenticated`() =
        runTest {
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultRefreshTokenResult = ResultWithError.Success(newTokens)
            }
            val useCase = createUseCase(authRepositoryFake)

            val result = useCase()

            assertIs<ResultWithError.Success<AuthTokens, *>>(result)
            assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
        }

    @Test
    fun `when TokenExpired then session becomes Unauthenticated and returns SessionExpired`() =
        runTest {
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultLogoutResult = ResultWithError.Success(Unit)
                defaultRefreshTokenResult =
                    ResultWithError.Failure(RefreshRepositoryError.TokenExpired)
            }

            val useCase = createUseCase(authRepositoryFake)

            val result = useCase()

            assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
            assertIs<TokenRefreshError.SessionExpired>(result.error)
            assertIs<AuthState.Unauthenticated>(authRepositoryFake.currentAuthState)
        }

    @Test
    fun `when SessionRevoked then session becomes Unauthenticated and returns SessionExpired`() =
        runTest {
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultLogoutResult = ResultWithError.Success(Unit)
                defaultRefreshTokenResult =
                    ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
            }
            val useCase = createUseCase(authRepositoryFake)

            val result = useCase()

            assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
            assertIs<TokenRefreshError.SessionExpired>(result.error)
            assertIs<AuthState.Unauthenticated>(authRepositoryFake.currentAuthState)
        }

    @Test
    fun `when LocalOperationFailed then returns LocalOperationFailed without logout`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultRefreshTokenResult = ResultWithError.Failure(
                RefreshRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            )
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.LocalOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
    }

    @Test
    fun `when RemoteOperationFailed then returns RemoteOperationFailed without logout`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultRefreshTokenResult = ResultWithError.Failure(
                RefreshRepositoryError.RemoteOperationFailed(RemoteError.Failed.ServiceDown),
            )
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.RemoteOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
    }

    @Test
    fun `when TokenExpired then settings cleanup is called with user scope key`() = runTest {
        val expectedKey = initialSession.toUserScopeKey()
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Success(Unit)
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.TokenExpired)
        }
        val settingsRepository = SettingsRepositoryFake(
            initialSettings = Settings(uiLanguage = UiLanguage.English),
        )
        val useCase = createUseCase(authRepositoryFake, settingsRepository)

        useCase()

        assertNotNull(settingsRepository.lastDeleteUserDataKey)
        kotlin.test.assertEquals(expectedKey, settingsRepository.lastDeleteUserDataKey)
    }

    @Test
    fun `when SessionRevoked then settings cleanup is called with user scope key`() = runTest {
        val expectedKey = initialSession.toUserScopeKey()
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Success(Unit)
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
        }
        val settingsRepository = SettingsRepositoryFake(
            initialSettings = Settings(uiLanguage = UiLanguage.English),
        )
        val useCase = createUseCase(authRepositoryFake, settingsRepository)

        useCase()

        assertNotNull(settingsRepository.lastDeleteUserDataKey)
        kotlin.test.assertEquals(expectedKey, settingsRepository.lastDeleteUserDataKey)
    }

    @Test
    fun `when refresh succeeds without token rotation then no settings cleanup`() = runTest {
        val sameRefreshToken = AuthTokens(
            accessToken = "new-access-token",
            refreshToken = "initial-refresh-token",
        )
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultRefreshTokenResult = ResultWithError.Success(sameRefreshToken)
        }
        val settingsRepository = SettingsRepositoryFake(
            initialSettings = Settings(uiLanguage = UiLanguage.English),
        )
        val useCase = createUseCase(authRepositoryFake, settingsRepository)

        useCase()

        assertNull(settingsRepository.lastDeleteUserDataKey)
    }

    @Test
    fun `when TokenExpired and local logout fails then returns LocalOperationFailed`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Failure(
                LogoutRepositoryError.LocalOperationFailed(LocalStorageError.AccessDenied),
            )
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.TokenExpired)
        }
        val settingsRepository = SettingsRepositoryFake(
            initialSettings = Settings(uiLanguage = UiLanguage.English),
        )
        val useCase = createUseCase(authRepositoryFake, settingsRepository)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.LocalOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
        kotlin.test.assertNull(settingsRepository.lastDeleteUserDataKey)
    }

    @Test
    fun `when SessionRevoked and local logout fails then returns LocalOperationFailed`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Failure(
                LogoutRepositoryError.LocalOperationFailed(LocalStorageError.AccessDenied),
            )
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
        }
        val settingsRepository = SettingsRepositoryFake(
            initialSettings = Settings(uiLanguage = UiLanguage.English),
        )
        val useCase = createUseCase(authRepositoryFake, settingsRepository)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.LocalOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
        kotlin.test.assertNull(settingsRepository.lastDeleteUserDataKey)
    }

    @Test
    fun `when refresh succeeds with token rotation then old scope settings are cleaned up`() =
        runTest {
            val expectedKey = initialSession.toUserScopeKey()
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultRefreshTokenResult = ResultWithError.Success(newTokens)
            }
            val settingsRepository = SettingsRepositoryFake(
                initialSettings = Settings(uiLanguage = UiLanguage.English),
            )
            val useCase = createUseCase(authRepositoryFake, settingsRepository)

            useCase()

            assertNotNull(settingsRepository.lastDeleteUserDataKey)
            kotlin.test.assertEquals(expectedKey, settingsRepository.lastDeleteUserDataKey)
        }
}
