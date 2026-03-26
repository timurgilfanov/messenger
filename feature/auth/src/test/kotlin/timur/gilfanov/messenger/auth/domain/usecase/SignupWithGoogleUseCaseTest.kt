package timur.gilfanov.messenger.auth.domain.usecase

import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.domain.validation.ProfileNameValidatorStub
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SignupWithGoogleUseCaseTest {

    private val idToken = GoogleIdToken("test-google-id-token")
    private val validName = "Alice"
    private val logger = NoOpLogger()

    private fun createUseCase(
        nameValidationError: ProfileNameValidationError? = null,
        repositoryResult: Failure<AuthSession, GoogleSignupRepositoryError>? = null,
    ): SignupWithGoogleUseCaseImpl {
        val nameValidator = ProfileNameValidatorStub(nameValidationError)
        val repository = AuthRepositoryFake()
        if (repositoryResult != null) {
            repository.enqueueSignupWithGoogleResult(repositoryResult)
        }
        return SignupWithGoogleUseCaseImpl(nameValidator, repository, logger)
    }

    @Test
    fun `when name validation fails then returns InvalidName without calling repository`() =
        runTest {
            val validationError = ProfileNameValidationError.LengthOutOfBounds(0, 1, 50)
            val useCase = createUseCase(nameValidationError = validationError)
            val result = useCase(idToken, "")
            val failure = assertIs<Failure<*, SignupWithGoogleUseCaseError>>(result)
            val error = assertIs<SignupWithGoogleUseCaseError.InvalidName>(failure.error)
            assertIs<ProfileNameValidationError.LengthOutOfBounds>(error.reason)
        }

    @Test
    fun `when repository succeeds then returns Success`() = runTest {
        val useCase = createUseCase()
        val result = useCase(idToken, validName)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository returns InvalidToken then returns InvalidToken`() = runTest {
        val useCase = createUseCase(
            repositoryResult = Failure(GoogleSignupRepositoryError.InvalidToken),
        )
        val result = useCase(idToken, validName)
        val failure = assertIs<Failure<*, SignupWithGoogleUseCaseError>>(result)
        assertIs<SignupWithGoogleUseCaseError.InvalidToken>(failure.error)
    }

    @Test
    fun `when repository returns AccountAlreadyExists then returns AccountAlreadyExists`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(GoogleSignupRepositoryError.AccountAlreadyExists),
            )
            val result = useCase(idToken, validName)
            val failure = assertIs<Failure<*, SignupWithGoogleUseCaseError>>(result)
            assertIs<SignupWithGoogleUseCaseError.AccountAlreadyExists>(failure.error)
        }

    @Test
    fun `when repository returns InvalidName then returns InvalidName`() = runTest {
        val nameError = ProfileNameValidationError.UnknownRuleViolation("policy violation")
        val useCase = createUseCase(
            repositoryResult = Failure(GoogleSignupRepositoryError.InvalidName(nameError)),
        )
        val result = useCase(idToken, validName)
        val failure = assertIs<Failure<*, SignupWithGoogleUseCaseError>>(result)
        val error = assertIs<SignupWithGoogleUseCaseError.InvalidName>(failure.error)
        assertIs<ProfileNameValidationError.UnknownRuleViolation>(error.reason)
    }

    @Test
    fun `when repository returns LocalOperationFailed then returns LocalOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    GoogleSignupRepositoryError.LocalOperationFailed(
                        LocalStorageError.TemporarilyUnavailable,
                    ),
                ),
            )
            val result = useCase(idToken, validName)
            val failure = assertIs<Failure<*, SignupWithGoogleUseCaseError>>(result)
            val error = assertIs<SignupWithGoogleUseCaseError.LocalOperationFailed>(failure.error)
            assertIs<LocalStorageError.TemporarilyUnavailable>(error.error)
        }

    @Test
    fun `when repository returns RemoteOperationFailed then returns RemoteOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    GoogleSignupRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                ),
            )
            val result = useCase(idToken, validName)
            val failure = assertIs<Failure<*, SignupWithGoogleUseCaseError>>(result)
            val error = assertIs<SignupWithGoogleUseCaseError.RemoteOperationFailed>(failure.error)
            assertIs<RemoteError.Failed.NetworkNotAvailable>(error.error)
        }
}
