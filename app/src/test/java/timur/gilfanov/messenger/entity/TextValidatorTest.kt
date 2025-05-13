package timur.gilfanov.messenger.entity

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import timur.gilfanov.messenger.entity.validation.TextValidator

class TextValidatorTest {

    @Test
    fun validateValidText() {
        val validator = TextValidator(maxLength = 100)
        val validText = "This is a valid text"
        val result = validator.validate(validText)
        assertTrue(result.isSuccess)
    }

    @Test
    fun validateTextAtMaxLength() {
        val maxLength = 10
        val validator = TextValidator(maxLength = maxLength)
        val textAtMaxLength = "a".repeat(maxLength)
        val result = validator.validate(textAtMaxLength)
        assertTrue(result.isSuccess)
    }

    @Test
    fun validateEmptyText() {
        val validator = TextValidator(maxLength = 100)
        val emptyText = ""
        val result = validator.validate(emptyText)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertEquals("Message text cannot be empty", exception.message)
    }

    @Test
    fun validateBlankText() {
        val validator = TextValidator(maxLength = 100)
        val blankText = "   "
        val result = validator.validate(blankText)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertEquals("Message text cannot be empty", exception.message)
    }

    @Test
    fun validateTextExceedingMaxLength() {
        val maxLength = 10
        val validator = TextValidator(maxLength = maxLength)
        val tooLongText = "a".repeat(maxLength + 1)
        val result = validator.validate(tooLongText)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertEquals("Message text cannot exceed $maxLength characters", exception.message)
    }
}
