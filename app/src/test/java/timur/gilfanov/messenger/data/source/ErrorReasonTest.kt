package timur.gilfanov.messenger.data.source

import kotlin.test.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ErrorReasonTest {

    @Test
    fun `ErrorReason equality is based on wrapped string value`() {
        val errorReason1 = ErrorReason("Disk is full")
        val errorReason2 = ErrorReason("Disk is full")
        val errorReason3 = ErrorReason("Different error")

        assertTrue(errorReason1 == errorReason2)
        assertTrue(errorReason1 != errorReason3)
        assertTrue(errorReason1.hashCode() == errorReason2.hashCode())
    }
}
