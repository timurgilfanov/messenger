package timur.gilfanov.messenger.auth.domain.validation

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.Password

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class CredentialsValidatorImplTest {

    private val validator = CredentialsValidatorImpl()

    private fun credentials(email: String = "user@example.com", password: String = "Password1") =
        Credentials(Email(email), Password(password))

    @Test
    fun `valid email and password returns success`() = runTest {
        val result = validator.validate(credentials())
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `blank email returns BlankEmail`() = runTest {
        val result = validator.validate(credentials(email = "  "))
        assertIs<Failure<*, CredentialsValidationError.BlankEmail>>(result)
    }

    @Test
    fun `email without at returns NoAtInEmail`() = runTest {
        val result = validator.validate(credentials(email = "userexample.com"))
        assertIs<Failure<*, CredentialsValidationError.NoAtInEmail>>(result)
    }

    @Test
    fun `email with nothing after at returns NoDomainAtEmail`() = runTest {
        val result = validator.validate(credentials(email = "user@"))
        assertIs<Failure<*, CredentialsValidationError.NoDomainAtEmail>>(result)
    }

    @Test
    fun `email longer than 254 chars returns EmailTooLong`() = runTest {
        val longEmail = "a".repeat(243) + "@example.com"
        val result = validator.validate(credentials(email = longEmail))
        val failure = assertIs<Failure<*, CredentialsValidationError.EmailTooLong>>(result)
        assertEquals(CredentialsValidatorImpl.MAX_EMAIL_LENGTH, failure.error.maxLength)
    }

    @Test
    fun `email with invalid format returns InvalidEmailFormat`() = runTest {
        val result = validator.validate(credentials(email = "user@.com"))
        assertIs<Failure<*, CredentialsValidationError.InvalidEmailFormat>>(result)
    }

    @Test
    fun `email errors take priority over password errors`() = runTest {
        val result = validator.validate(credentials(email = "bad", password = "bad"))
        assertIs<Failure<*, CredentialsValidationError>>(result)
        assertIs<CredentialsValidationError.NoAtInEmail>(result.error)
    }

    @Test
    fun `password shorter than 8 chars returns PasswordTooShort`() = runTest {
        val result = validator.validate(credentials(password = "Pass1"))
        val failure = assertIs<Failure<*, CredentialsValidationError.PasswordTooShort>>(result)
        assertEquals(CredentialsValidatorImpl.MIN_PASSWORD_LENGTH, failure.error.minLength)
    }

    @Test
    fun `password longer than 128 chars returns PasswordTooLong`() = runTest {
        val longPassword = "P1" + "a".repeat(127)
        val result = validator.validate(credentials(password = longPassword))
        val failure = assertIs<Failure<*, CredentialsValidationError.PasswordTooLong>>(result)
        assertEquals(CredentialsValidatorImpl.MAX_PASSWORD_LENGTH, failure.error.maxLength)
    }

    @Test
    fun `password without digit returns PasswordMustContainNumbers`() = runTest {
        val result = validator.validate(credentials(password = "PasswordA"))
        val failure =
            assertIs<Failure<*, CredentialsValidationError.PasswordMustContainNumbers>>(result)
        assertEquals(1, failure.error.minNumbers)
    }

    @Test
    fun `password without letter returns PasswordMustContainAlphabet`() = runTest {
        val result = validator.validate(credentials(password = "12345678"))
        val failure =
            assertIs<Failure<*, CredentialsValidationError.PasswordMustContainAlphabet>>(result)
        assertEquals(1, failure.error.minAlphabet)
    }

    @Test
    fun `password exactly at min length is valid`() = runTest {
        val result = validator.validate(credentials(password = "Pass1234"))
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `password exactly at max length is valid`() = runTest {
        val maxPassword = "P1" + "a".repeat(126)
        val result = validator.validate(credentials(password = maxPassword))
        assertIs<Success<Unit, *>>(result)
    }
}
