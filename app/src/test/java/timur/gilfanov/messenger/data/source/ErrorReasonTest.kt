package timur.gilfanov.messenger.data.source

import kotlin.test.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ErrorReasonTest {

    @Test
    fun `two exceptions are not equal`() {
        val exception1 = kotlinx.io.IOException("Disk is full")
        val exception2 = kotlinx.io.IOException("Disk is full")

        assertFalse(exception1 == exception2)
    }

    @Test
    fun `two ErrorReason are equal`() {
        val errorReason1 = ErrorReason("Disk is full")
        val errorReason2 = ErrorReason("Disk is full")

        assertTrue(errorReason1 == errorReason2)
    }
}
