package timur.gilfanov.messenger.auth.domain.usecase

import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.validation.ProfileNameValidatorStub
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
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SignupWithCredentialsUseCaseTest {

    private val credentials = Credentials(Email("user@example.com"), Password("Password1"))
    private val validName = "Alice"
    private val logger = NoOpLogger()

    private fun createUseCase(
        credentialsValidatorError: CredentialsValidationError? = null,
        nameValidationError: ProfileNameValidationError? = null,
        repositoryResult: Failure<AuthSession, SignupRepositoryError>? = null,
    ): SignupWithCredentialsUseCaseImpl {
        val validator = CredentialsValidatorStub(credentialsValidatorError)
        val nameValidator = ProfileNameValidatorStub(nameValidationError)
        val repository = AuthRepositoryFake()
        if (repositoryResult != null) {
            repository.enqueueSignupResult(repositoryResult)
        }
        return SignupWithCredentialsUseCaseImpl(validator, nameValidator, repository, logger)
    }

    @Test
    fun `when credentials validation fails then returns ValidationFailed`() = runTest {
        val validationError = CredentialsValidationError.BlankEmail
        val useCase = createUseCase(credentialsValidatorError = validationError)
        val result = useCase(credentials, validName)
        val failure = assertIs<Failure<*, SignupWithCredentialsUseCaseError>>(result)
        val error = assertIs<SignupWithCredentialsUseCaseError.ValidationFailed>(failure.error)
        assertIs<CredentialsValidationError.BlankEmail>(error.error)
    }

    @Test
    fun `when name validation fails then returns InvalidName without calling repository`() =
        runTest {
            val nameError = ProfileNameValidationError.LengthOutOfBounds(0, 1, 50)
            val useCase = createUseCase(nameValidationError = nameError)
            val result = useCase(credentials, "")
            val failure = assertIs<Failure<*, SignupWithCredentialsUseCaseError>>(result)
            val error = assertIs<SignupWithCredentialsUseCaseError.InvalidName>(failure.error)
            assertIs<ProfileNameValidationError.LengthOutOfBounds>(error.reason)
        }

    @Test
    fun `when repository succeeds then returns Success`() = runTest {
        val useCase = createUseCase()
        val result = useCase(credentials, validName)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository returns InvalidEmail then returns InvalidEmail`() = runTest {
        val emailError = EmailValidationError.EmailTaken
        val useCase = createUseCase(
            repositoryResult = Failure(SignupRepositoryError.InvalidEmail(emailError)),
        )
        val result = useCase(credentials, validName)
        val failure = assertIs<Failure<*, SignupWithCredentialsUseCaseError>>(result)
        val error = assertIs<SignupWithCredentialsUseCaseError.InvalidEmail>(failure.error)
        assertIs<EmailValidationError.EmailTaken>(error.reason)
    }

    @Test
    fun `when repository returns InvalidPassword then returns InvalidPassword`() = runTest {
        val passwordError = PasswordValidationError.PasswordTooShort(8)
        val useCase = createUseCase(
            repositoryResult = Failure(SignupRepositoryError.InvalidPassword(passwordError)),
        )
        val result = useCase(credentials, validName)
        val failure = assertIs<Failure<*, SignupWithCredentialsUseCaseError>>(result)
        val error = assertIs<SignupWithCredentialsUseCaseError.InvalidPassword>(failure.error)
        assertIs<PasswordValidationError.PasswordTooShort>(error.reason)
    }

    @Test
    fun `when repository returns InvalidName then returns InvalidName`() = runTest {
        val nameError = ProfileNameValidationError.UnknownRuleViolation("policy violation")
        val useCase = createUseCase(
            repositoryResult = Failure(SignupRepositoryError.InvalidName(nameError)),
        )
        val result = useCase(credentials, validName)
        val failure = assertIs<Failure<*, SignupWithCredentialsUseCaseError>>(result)
        val error = assertIs<SignupWithCredentialsUseCaseError.InvalidName>(failure.error)
        assertIs<ProfileNameValidationError.UnknownRuleViolation>(error.reason)
    }

    @Test
    fun `when repository returns LocalOperationFailed then returns LocalOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    SignupRepositoryError.LocalOperationFailed(
                        LocalStorageError.TemporarilyUnavailable,
                    ),
                ),
            )
            val result = useCase(credentials, validName)
            val failure = assertIs<Failure<*, SignupWithCredentialsUseCaseError>>(result)
            val error =
                assertIs<SignupWithCredentialsUseCaseError.LocalOperationFailed>(failure.error)
            assertIs<LocalStorageError.TemporarilyUnavailable>(error.error)
        }

    @Test
    fun `when repository returns RemoteOperationFailed then returns RemoteOperationFailed`() =
        runTest {
            val useCase = createUseCase(
                repositoryResult = Failure(
                    SignupRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                ),
            )
            val result = useCase(credentials, validName)
            val failure = assertIs<Failure<*, SignupWithCredentialsUseCaseError>>(result)
            val error =
                assertIs<SignupWithCredentialsUseCaseError.RemoteOperationFailed>(failure.error)
            assertIs<RemoteError.Failed.NetworkNotAvailable>(error.error)
        }
}
