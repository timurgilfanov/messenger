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
import timur.gilfanov.messenger.auth.data.source.remote.RegisterError
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSourceFake
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailUnknownError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthRepositorySignupTest {

    private val credentials = Credentials(Email("user@example.com"), Password("secret123"))
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
    fun `signup success stores session and sets Authenticated EMAIL provider`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, name)

        assertIs<ResultWithError.Success<AuthSession, SignupRepositoryError>>(result)
        kotlin.test.assertEquals(AuthProvider.EMAIL, result.data.provider)
        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
        }
    }

    @Test
    fun `signup success updates authState to Authenticated`() = runTest {
        val repo = createRepo(testScope = this)
        advanceUntilIdle()

        repo.signup(credentials, name)

        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
        }
    }

    @Test
    fun `signup InvalidEmail EmailTaken returns InvalidEmail error with EmailTaken reason`() =
        runTest {
            val remote = RemoteAuthDataSourceFake()
            remote.enqueueRegister(
                ResultWithError.Failure(
                    RegisterError.InvalidEmail(SignupEmailError.EmailTaken),
                ),
            )
            val repo = createRepo(remoteDataSource = remote, testScope = this)
            advanceUntilIdle()

            val result = repo.signup(credentials, name)

            val failure =
                assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
            val error = assertIs<SignupRepositoryError.InvalidEmail>(failure.error)
            assertIs<SignupEmailError.EmailTaken>(error.reason)
        }

    @Test
    fun `signup InvalidEmail EmailUnknownError returns InvalidEmail with EmailUnknownError`() =
        runTest {
            val remote = RemoteAuthDataSourceFake()
            remote.enqueueRegister(
                ResultWithError.Failure(
                    RegisterError.InvalidEmail(EmailUnknownError("EMAIL_NOT_EXISTS")),
                ),
            )
            val repo = createRepo(remoteDataSource = remote, testScope = this)
            advanceUntilIdle()

            val result = repo.signup(credentials, name)

            val failure =
                assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
            val error = assertIs<SignupRepositoryError.InvalidEmail>(failure.error)
            assertIs<EmailUnknownError>(error.reason)
        }

    @Test
    fun `signup InvalidPassword returns InvalidPassword error with reason preserved`() = runTest {
        val passwordError = PasswordValidationError.PasswordTooShort(minLength = 8)
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRegister(
            ResultWithError.Failure(RegisterError.InvalidPassword(passwordError)),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, name)

        val failure = assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
        val error = assertIs<SignupRepositoryError.InvalidPassword>(failure.error)
        kotlin.test.assertEquals(passwordError, error.reason)
    }

    @Test
    fun `signup InvalidName returns InvalidName error with reason preserved`() = runTest {
        val nameError = ProfileNameValidationError.LengthOutOfBounds(length = 1, min = 2, max = 50)
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRegister(
            ResultWithError.Failure(RegisterError.InvalidName(nameError)),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, name)

        val failure = assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
        val error = assertIs<SignupRepositoryError.InvalidName>(failure.error)
        kotlin.test.assertEquals(nameError, error.reason)
    }

    @Test
    fun `signup RemoteDataSource error returns RemoteOperationFailed`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRegister(
            ResultWithError.Failure(
                RegisterError.RemoteDataSource(RemoteDataSourceError.ServerError),
            ),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, name)

        val failure = assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
        assertIs<SignupRepositoryError.RemoteOperationFailed>(failure.error)
    }

    @Test
    fun `signup local storage AccessDenied maps to LocalOperationFailed AccessDenied`() = runTest {
        val storage = LocalAuthDataSourceFake()
        storage.enqueueSaveSession(
            ResultWithError.Failure(LocalAuthDataSourceError.AccessDenied),
        )
        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, name)

        val failure =
            assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
        val error = assertIs<SignupRepositoryError.LocalOperationFailed>(failure.error)
        assertIs<LocalStorageError.AccessDenied>(error.error)
    }

    @Test
    fun `signup local storage StorageFull failure returns LocalOperationFailed with StorageFull`() =
        runTest {
            val storage = LocalAuthDataSourceFake()
            storage.enqueueSaveSession(
                ResultWithError.Failure(LocalAuthDataSourceError.StorageFull),
            )
            val repo = createRepo(sessionStorage = storage, testScope = this)
            advanceUntilIdle()

            val result = repo.signup(credentials, name)

            val failure =
                assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
            val error = assertIs<SignupRepositoryError.LocalOperationFailed>(failure.error)
            assertIs<LocalStorageError.StorageFull>(error.error)
        }
}
