package timur.gilfanov.messenger.domain.entity

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidator

@Category(Unit::class)
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
        assertEquals(TextValidationError.Empty, (result as Failure).error)
    }

    @Test
    fun validateBlankText() {
        val validator = TextValidator(maxLength = 100)
        val blankText = "   "
        val result = validator.validate(blankText)
        assertTrue(result.isFailure)
        assertEquals(TextValidationError.Empty, (result as Failure).error)
    }

    @Test
    fun validateTextExceedingMaxLength() {
        val maxLength = 10
        val validator = TextValidator(maxLength = maxLength)
        val tooLongText = "a".repeat(maxLength + 1)
        val result = validator.validate(tooLongText)
        assertTrue(result.isFailure)
        val error = (result as Failure).error
        assertTrue(error is TextValidationError.TooLong)
        assertEquals(maxLength, error.maxLength)
    }
}
