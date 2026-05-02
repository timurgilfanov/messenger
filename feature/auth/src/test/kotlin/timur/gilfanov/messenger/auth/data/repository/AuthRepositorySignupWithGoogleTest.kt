package timur.gilfanov.messenger.auth.data.repository

import app.cash.turbine.test
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceError
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceFake
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSourceFake
import timur.gilfanov.messenger.auth.data.source.remote.SignupWithGoogleError
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthRepositorySignupWithGoogleTest {

    private val idToken = GoogleIdToken("google-id-token")
    private val name = "Alice"

    private fun createRepo(
        remoteDataSource: RemoteAuthDataSourceFake = RemoteAuthDataSourceFake(),
        sessionStorage: LocalAuthDataSourceFake = LocalAuthDataSourceFake(),
        testScope: kotlinx.coroutines.test.TestScope,
    ): AuthRepositoryImpl = AuthRepositoryImpl(
        remoteDataSource = remoteDataSource,
        localDataSource = sessionStorage,
        coroutineScope = testScope,
        logger = NoOpLogger(),
    )

    @Test
    fun `signupWithGoogle success stores session and sets Authenticated GOOGLE provider`() =
        runTest {
            val storage = LocalAuthDataSourceFake()
            val repo = createRepo(sessionStorage = storage, testScope = this)
            advanceUntilIdle()

            val result = repo.signupWithGoogle(idToken, name)

            assertIs<ResultWithError.Success<AuthSession, GoogleSignupRepositoryError>>(result)
            kotlin.test.assertEquals(AuthProvider.GOOGLE, result.data.provider)
            repo.authState.test {
                assertIs<AuthState.Authenticated>(awaitItem())
            }
        }

    @Test
    fun `signupWithGoogle success updates authState to Authenticated`() = runTest {
        val repo = createRepo(testScope = this)
        advanceUntilIdle()

        repo.signupWithGoogle(idToken, name)

        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
        }
    }

    @Test
    fun `signupWithGoogle InvalidToken returns InvalidToken error`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueSignupWithGoogle(
            ResultWithError.Failure(SignupWithGoogleError.InvalidToken),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signupWithGoogle(idToken, name)

        val failure =
            assertIs<ResultWithError.Failure<AuthSession, GoogleSignupRepositoryError>>(result)
        assertIs<GoogleSignupRepositoryError.InvalidToken>(failure.error)
    }

    @Test
    fun `signupWithGoogle AccountAlreadyExists returns AccountAlreadyExists error`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueSignupWithGoogle(
            ResultWithError.Failure(SignupWithGoogleError.AccountAlreadyExists),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signupWithGoogle(idToken, name)

        val failure =
            assertIs<ResultWithError.Failure<AuthSession, GoogleSignupRepositoryError>>(result)
        assertIs<GoogleSignupRepositoryError.AccountAlreadyExists>(failure.error)
    }

    @Test
    fun `signupWithGoogle InvalidName returns InvalidName error with reason preserved`() = runTest {
        val nameError = ProfileNameValidationError.LengthOutOfBounds(length = 1, min = 2, max = 50)
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueSignupWithGoogle(
            ResultWithError.Failure(SignupWithGoogleError.InvalidName(nameError)),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signupWithGoogle(idToken, name)

        val failure =
            assertIs<ResultWithError.Failure<AuthSession, GoogleSignupRepositoryError>>(result)
        val error = assertIs<GoogleSignupRepositoryError.InvalidName>(failure.error)
        kotlin.test.assertEquals(nameError, error.reason)
    }

    @Test
    fun `signupWithGoogle RemoteDataSource error returns RemoteOperationFailed`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueSignupWithGoogle(
            ResultWithError.Failure(
                SignupWithGoogleError.RemoteDataSource(RemoteDataSourceError.ServerError),
            ),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signupWithGoogle(idToken, name)

        val failure =
            assertIs<ResultWithError.Failure<AuthSession, GoogleSignupRepositoryError>>(result)
        assertIs<GoogleSignupRepositoryError.RemoteOperationFailed>(failure.error)
    }

    @Test
    fun `signupWithGoogle local storage failure returns LocalOperationFailed`() = runTest {
        val storage = LocalAuthDataSourceFake()
        storage.enqueueSaveSession(
            ResultWithError.Failure(LocalAuthDataSourceError.AccessDenied),
        )
        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.signupWithGoogle(idToken, name)

        val failure =
            assertIs<ResultWithError.Failure<AuthSession, GoogleSignupRepositoryError>>(result)
        val error = assertIs<GoogleSignupRepositoryError.LocalOperationFailed>(failure.error)
        assertIs<LocalStorageError.AccessDenied>(error.error)
    }

    @Test
    fun `signupWithGoogle StorageFull maps to LocalStorageError StorageFull`() = runTest {
        val storage = LocalAuthDataSourceFake()
        storage.enqueueSaveSession(
            ResultWithError.Failure(LocalAuthDataSourceError.StorageFull),
        )
        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.signupWithGoogle(idToken, name)

        val failure =
            assertIs<ResultWithError.Failure<AuthSession, GoogleSignupRepositoryError>>(result)
        val error = assertIs<GoogleSignupRepositoryError.LocalOperationFailed>(failure.error)
        assertIs<LocalStorageError.StorageFull>(error.error)
    }
}
