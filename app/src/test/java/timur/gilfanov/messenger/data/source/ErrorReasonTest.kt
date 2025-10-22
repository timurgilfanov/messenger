package timur.gilfanov.messenger.data.source

import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ErrorReasonTest {

    @Test
    fun `two ErrorReason are equal`() {
        val errorReason1 = ErrorReason("Disk is full")
        val errorReason2 = ErrorReason("Disk is full")

        assertTrue(errorReason1 == errorReason2)
    }
}
