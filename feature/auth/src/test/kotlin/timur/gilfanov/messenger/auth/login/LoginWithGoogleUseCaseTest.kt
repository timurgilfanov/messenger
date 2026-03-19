package timur.gilfanov.messenger.auth.login

import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LoginWithGoogleUseCaseTest {

    private val idToken = GoogleIdToken("test-google-id-token")
    private val logger = NoOpLogger()

    private fun createUseCase(
        repositoryResult: Failure<AuthSession, GoogleLoginRepositoryError>? = null,
    ): LoginWithGoogleUseCaseImpl {
        val repository = AuthRepositoryFake()
        if (repositoryResult != null) {
            repository.enqueueLoginWithGoogleResult(repositoryResult)
        }
        return LoginWithGoogleUseCaseImpl(repository, logger)
    }

    @Test
    fun `when repository succeeds then returns Success`() = runTest {
        val useCase = createUseCase()
        val result = useCase(idToken)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository returns InvalidToken then returns InvalidToken`() = runTest {
        val useCase =
            createUseCase(repositoryResult = Failure(GoogleLoginRepositoryError.InvalidToken))
        val result = useCase(idToken)
        val failure = assertIs<Failure<*, GoogleLoginUseCaseError>>(result)
        assertIs<GoogleLoginUseCaseError.InvalidToken>(failure.error)
    }

    @Test
    fun `when repository returns AccountNotFound then returns AccountNotFound`() = runTest {
        val useCase =
            createUseCase(repositoryResult = Failure(GoogleLoginRepositoryError.AccountNotFound))
        val result = useCase(idToken)
        val failure = assertIs<Failure<*, GoogleLoginUseCaseError>>(result)
        assertIs<GoogleLoginUseCaseError.AccountNotFound>(failure.error)
    }

    @Test
    fun `when repository returns AccountSuspended then returns AccountSuspended`() = runTest {
        val useCase =
            createUseCase(repositoryResult = Failure(GoogleLoginRepositoryError.AccountSuspended))
        val result = useCase(idToken)
        val failure = assertIs<Failure<*, GoogleLoginUseCaseError>>(result)
        assertIs<GoogleLoginUseCaseError.AccountSuspended>(failure.error)
    }

    @Test
    fun `when repository returns LocalOperationFailed then returns LocalOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    GoogleLoginRepositoryError.LocalOperationFailed(
                        LocalStorageError.TemporarilyUnavailable,
                    ),
                ),
            )
            val result = useCase(idToken)
            val failure = assertIs<Failure<*, GoogleLoginUseCaseError>>(result)
            val error = assertIs<GoogleLoginUseCaseError.LocalOperationFailed>(failure.error)
            assertIs<LocalStorageError.TemporarilyUnavailable>(error.error)
        }

    @Test
    fun `when repository returns RemoteOperationFailed then returns RemoteOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    GoogleLoginRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                ),
            )
            val result = useCase(idToken)
            val failure = assertIs<Failure<*, GoogleLoginUseCaseError>>(result)
            val error = assertIs<GoogleLoginUseCaseError.RemoteOperationFailed>(failure.error)
            assertIs<RemoteError.Failed.NetworkNotAvailable>(error.error)
        }
}
