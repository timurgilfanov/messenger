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
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

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
        val failure = assertIs<Failure<*, CredentialsValidationError.Email>>(result)
        assertIs<EmailValidationError.BlankEmail>(failure.error.reason)
    }

    @Test
    fun `email without at returns NoAtInEmail`() = runTest {
        val result = validator.validate(credentials(email = "userexample.com"))
        val failure = assertIs<Failure<*, CredentialsValidationError.Email>>(result)
        assertIs<EmailValidationError.NoAtInEmail>(failure.error.reason)
    }

    @Test
    fun `email with nothing after at returns NoDomainAtEmail`() = runTest {
        val result = validator.validate(credentials(email = "user@"))
        val failure = assertIs<Failure<*, CredentialsValidationError.Email>>(result)
        assertIs<EmailValidationError.NoDomainAtEmail>(failure.error.reason)
    }

    @Test
    fun `email longer than 254 chars returns EmailTooLong`() = runTest {
        val longEmail = "a".repeat(243) + "@example.com"
        val result = validator.validate(credentials(email = longEmail))
        val failure = assertIs<Failure<*, CredentialsValidationError.Email>>(result)
        val tooLong = assertIs<EmailValidationError.EmailTooLong>(failure.error.reason)
        assertEquals(CredentialsValidatorImpl.MAX_EMAIL_LENGTH, tooLong.maxLength)
    }

    @Test
    fun `email with invalid format returns InvalidEmailFormat`() = runTest {
        val result = validator.validate(credentials(email = "user@.com"))
        val failure = assertIs<Failure<*, CredentialsValidationError.Email>>(result)
        assertIs<EmailValidationError.InvalidEmailFormat>(failure.error.reason)
    }

    @Test
    fun `email errors take priority over password errors`() = runTest {
        val result = validator.validate(credentials(email = "bad", password = "bad"))
        val failure = assertIs<Failure<*, CredentialsValidationError.Email>>(result)
        assertIs<EmailValidationError.NoAtInEmail>(failure.error.reason)
    }

    @Test
    fun `password shorter than 8 chars returns PasswordTooShort`() = runTest {
        val result = validator.validate(credentials(password = "Pass1"))
        val failure = assertIs<Failure<*, CredentialsValidationError.Password>>(result)
        val tooShort = assertIs<PasswordValidationError.PasswordTooShort>(failure.error.reason)
        assertEquals(CredentialsValidatorImpl.MIN_PASSWORD_LENGTH, tooShort.minLength)
    }

    @Test
    fun `password longer than 128 chars returns PasswordTooLong`() = runTest {
        val longPassword = "P1" + "a".repeat(127)
        val result = validator.validate(credentials(password = longPassword))
        val failure = assertIs<Failure<*, CredentialsValidationError.Password>>(result)
        val tooLong = assertIs<PasswordValidationError.PasswordTooLong>(failure.error.reason)
        assertEquals(CredentialsValidatorImpl.MAX_PASSWORD_LENGTH, tooLong.maxLength)
    }

    @Test
    fun `password without digit returns PasswordMustContainNumbers`() = runTest {
        val result = validator.validate(credentials(password = "PasswordA"))
        val failure = assertIs<Failure<*, CredentialsValidationError.Password>>(result)
        val mustContain =
            assertIs<PasswordValidationError.PasswordMustContainNumbers>(failure.error.reason)
        assertEquals(1, mustContain.minNumbers)
    }

    @Test
    fun `password without letter returns PasswordMustContainAlphabet`() = runTest {
        val result = validator.validate(credentials(password = "12345678"))
        val failure = assertIs<Failure<*, CredentialsValidationError.Password>>(result)
        val mustContain =
            assertIs<PasswordValidationError.PasswordMustContainAlphabet>(failure.error.reason)
        assertEquals(1, mustContain.minAlphabet)
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

    @Test
    fun `validate email alone returns success for valid email`() = runTest {
        val result = validator.validate(Email("user@example.com"))
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `validate email alone returns error for invalid email`() = runTest {
        val result = validator.validate(Email("notanemail"))
        val failure = assertIs<Failure<*, EmailValidationError>>(result)
        assertIs<EmailValidationError.NoAtInEmail>(failure.error)
    }

    @Test
    fun `validate password alone returns success for valid password`() = runTest {
        val result = validator.validate(Password("Password1"))
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `validate password alone returns error for invalid password`() = runTest {
        val result = validator.validate(Password("short"))
        val failure = assertIs<Failure<*, PasswordValidationError>>(result)
        assertIs<PasswordValidationError.PasswordTooShort>(failure.error)
    }

    @Test
    fun `password equal to email returns PasswordEqualToEmail`() = runTest {
        val result = validator.validate(
            credentials(email = "user1@example.com", password = "user1@example.com"),
        )
        val failure = assertIs<Failure<*, CredentialsValidationError.Password>>(result)
        assertIs<PasswordValidationError.PasswordEqualToEmail>(failure.error.reason)
    }

    @Test
    fun `password different from email passes`() = runTest {
        val result = validator.validate(
            credentials(email = "user@example.com", password = "user@example.com1"),
        )
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `password fails standalone validation even when equal to email`() = runTest {
        // "a@b.cd" is a valid email but only 6 chars — below MIN_PASSWORD_LENGTH
        val result = validator.validate(
            credentials(email = "a@b.cd", password = "a@b.cd"),
        )
        val failure = assertIs<Failure<*, CredentialsValidationError.Password>>(result)
        assertIs<PasswordValidationError.PasswordTooShort>(failure.error.reason)
    }

    @Test
    fun `invalid email takes priority over password equal to email`() = runTest {
        val result = validator.validate(
            credentials(email = "notanemail1", password = "notanemail1"),
        )
        val failure = assertIs<Failure<*, CredentialsValidationError.Email>>(result)
        assertIs<EmailValidationError.NoAtInEmail>(failure.error.reason)
    }

    @Test
    fun `password equal to email in different case passes`() = runTest {
        val result = validator.validate(
            credentials(email = "User1@Example.com", password = "user1@example.com"),
        )
        assertIs<Success<Unit, *>>(result)
    }
}
