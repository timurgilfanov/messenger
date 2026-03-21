package timur.gilfanov.messenger.auth.ui

import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.domain.usecase.LoginUseCaseError
import timur.gilfanov.messenger.auth.domain.usecase.LoginWithCredentialsUseCaseImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidatorStub
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LoginWithCredentialsUseCaseTest {

    private val credentials = Credentials(Email("user@example.com"), Password("Password1"))
    private val logger = NoOpLogger()

    private fun createUseCase(
        validatorError: CredentialsValidationError? = null,
        repositoryResult: Failure<AuthSession, LoginRepositoryError>? = null,
    ): LoginWithCredentialsUseCaseImpl {
        val validator = CredentialsValidatorStub(validatorError)
        val repository = AuthRepositoryFake()
        if (repositoryResult != null) {
            repository.enqueueLoginWithCredentialsResult(repositoryResult)
        }
        return LoginWithCredentialsUseCaseImpl(validator, repository, logger)
    }

    @Test
    fun `when validation fails then returns ValidationFailed`() = runTest {
        val validationError = CredentialsValidationError.BlankEmail
        val useCase = createUseCase(validatorError = validationError)
        val result = useCase(credentials)
        val failure = assertIs<Failure<*, LoginUseCaseError>>(result)
        assertIs<LoginUseCaseError.ValidationFailed>(failure.error)
        assertIs<CredentialsValidationError.BlankEmail>(
            (failure.error as LoginUseCaseError.ValidationFailed).error,
        )
    }

    @Test
    fun `when repository succeeds then returns Success`() = runTest {
        val useCase = createUseCase()
        val result = useCase(credentials)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository returns InvalidCredentials then returns InvalidCredentials`() = runTest {
        val useCase =
            createUseCase(repositoryResult = Failure(LoginRepositoryError.InvalidCredentials))
        val result = useCase(credentials)
        val failure = assertIs<Failure<*, LoginUseCaseError>>(result)
        assertIs<LoginUseCaseError.InvalidCredentials>(failure.error)
    }

    @Test
    fun `when repository returns EmailNotVerified then returns EmailNotVerified`() = runTest {
        val useCase =
            createUseCase(repositoryResult = Failure(LoginRepositoryError.EmailNotVerified))
        val result = useCase(credentials)
        val failure = assertIs<Failure<*, LoginUseCaseError>>(result)
        assertIs<LoginUseCaseError.EmailNotVerified>(failure.error)
    }

    @Test
    fun `when repository returns AccountSuspended then returns AccountSuspended`() = runTest {
        val useCase =
            createUseCase(repositoryResult = Failure(LoginRepositoryError.AccountSuspended))
        val result = useCase(credentials)
        val failure = assertIs<Failure<*, LoginUseCaseError>>(result)
        assertIs<LoginUseCaseError.AccountSuspended>(failure.error)
    }

    @Test
    fun `when repository returns LocalOperationFailed then returns LocalOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    LoginRepositoryError.LocalOperationFailed(
                        LocalStorageError.TemporarilyUnavailable,
                    ),
                ),
            )
            val result = useCase(credentials)
            val failure = assertIs<Failure<*, LoginUseCaseError>>(result)
            val error = assertIs<LoginUseCaseError.LocalOperationFailed>(failure.error)
            assertIs<LocalStorageError.TemporarilyUnavailable>(error.error)
        }

    @Test
    fun `when repository returns RemoteOperationFailed then returns RemoteOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    LoginRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                ),
            )
            val result = useCase(credentials)
            val failure = assertIs<Failure<*, LoginUseCaseError>>(result)
            val error = assertIs<LoginUseCaseError.RemoteOperationFailed>(failure.error)
            assertIs<RemoteError.Failed.NetworkNotAvailable>(error.error)
        }
}
