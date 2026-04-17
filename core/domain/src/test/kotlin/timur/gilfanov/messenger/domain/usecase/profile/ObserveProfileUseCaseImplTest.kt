package timur.gilfanov.messenger.domain.usecase.profile

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ObserveProfileUseCaseImplTest {

    private val testSession = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
    )

    @Test
    fun `emits profile when authenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val useCase = ObserveProfileUseCaseImpl(authRepository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Profile, ObserveProfileError>>(result)
            assertEquals("Timur", result.data.name)
        }
    }

    @Test
    fun `emits Unauthorized when unauthenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
        val useCase = ObserveProfileUseCaseImpl(authRepository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Profile, ObserveProfileError>>(result)
            assertEquals(ObserveProfileError.Unauthorized, result.error)
        }
    }

    @Test
    fun `emits nothing while auth state is Loading`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val useCase = ObserveProfileUseCaseImpl(authRepository)

        useCase().test {
            expectNoEvents()
        }
    }

    @Test
    fun `emits profile after Loading transitions to Authenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val useCase = ObserveProfileUseCaseImpl(authRepository)

        useCase().test {
            expectNoEvents()

            authRepository.setState(AuthState.Authenticated(testSession))

            val result = awaitItem()
            assertIs<ResultWithError.Success<Profile, ObserveProfileError>>(result)
            assertEquals("Timur", result.data.name)
        }
    }

    @Test
    fun `emits Unauthorized after Loading transitions to Unauthenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val useCase = ObserveProfileUseCaseImpl(authRepository)

        useCase().test {
            expectNoEvents()

            authRepository.setState(AuthState.Unauthenticated)

            val result = awaitItem()
            assertIs<ResultWithError.Failure<Profile, ObserveProfileError>>(result)
            assertEquals(ObserveProfileError.Unauthorized, result.error)
        }
    }

    @Test
    fun `emits Unauthorized when Authenticated transitions to Unauthenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val useCase = ObserveProfileUseCaseImpl(authRepository)

        useCase().test {
            val first = awaitItem()
            assertIs<ResultWithError.Success<Profile, ObserveProfileError>>(first)

            authRepository.setState(AuthState.Unauthenticated)

            val second = awaitItem()
            assertIs<ResultWithError.Failure<Profile, ObserveProfileError>>(second)
            assertEquals(ObserveProfileError.Unauthorized, second.error)
        }
    }

    @Test
    fun `emits profile after recovering from Unauthenticated to Authenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
        val useCase = ObserveProfileUseCaseImpl(authRepository)

        useCase().test {
            val first = awaitItem()
            assertIs<ResultWithError.Failure<Profile, ObserveProfileError>>(first)
            assertEquals(ObserveProfileError.Unauthorized, first.error)

            authRepository.setState(AuthState.Authenticated(testSession))

            val second = awaitItem()
            assertIs<ResultWithError.Success<Profile, ObserveProfileError>>(second)
            assertEquals("Timur", second.data.name)
        }
    }
}