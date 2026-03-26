package timur.gilfanov.messenger.auth.validation

import kotlin.test.assertIs
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

@Category(Unit::class)
class ProfileNameValidatorImplTest {

    private val validator = ProfileNameValidatorImpl()

    @Test
    fun `empty name returns LengthOutOfBounds`() {
        val result = validator.validate("")
        assertIs<Failure<*, ProfileNameValidationError.LengthOutOfBounds>>(result)
    }

    @Test
    fun `blank name returns LengthOutOfBounds`() {
        val result = validator.validate("   ")
        assertIs<Failure<*, ProfileNameValidationError.LengthOutOfBounds>>(result)
    }

    @Test
    fun `name at minimum length returns success`() {
        val result = validator.validate("A".repeat(ProfileNameValidatorImpl.MIN_NAME_LENGTH))
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `name at maximum length returns success`() {
        val result = validator.validate("A".repeat(ProfileNameValidatorImpl.MAX_NAME_LENGTH))
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `name exceeding maximum length returns LengthOutOfBounds`() {
        val result = validator.validate("A".repeat(ProfileNameValidatorImpl.MAX_NAME_LENGTH + 1))
        assertIs<Failure<*, ProfileNameValidationError.LengthOutOfBounds>>(result)
    }

    @Test
    fun `valid name returns success`() {
        val result = validator.validate("Alice Smith")
        assertIs<Success<Unit, *>>(result)
    }
}
