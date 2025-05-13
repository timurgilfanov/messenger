package timur.gilfanov.messenger.entity

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus

class DeliveryStatusTest {

    @Test
    fun sendingWithValidProgressValues() {
        val sending0 = DeliveryStatus.Sending(0)
        assertEquals(0, sending0.progress)

        val sending50 = DeliveryStatus.Sending(50)
        assertEquals(50, sending50.progress)

        val sending100 = DeliveryStatus.Sending(100)
        assertEquals(100, sending100.progress)
    }

    @Test
    fun sendingWithInvalidProgressValuesThrowsException() {
        val belowMinException = assertFailsWith<IllegalArgumentException> {
            DeliveryStatus.Sending(-1)
        }
        assertEquals("Progress must be between 0 and 100", belowMinException.message)

        val aboveMaxException = assertFailsWith<IllegalArgumentException> {
            DeliveryStatus.Sending(101)
        }
        assertEquals("Progress must be between 0 and 100", aboveMaxException.message)
    }

    @Test
    fun sendingEqualityWithSameProgress() {
        val sending1 = DeliveryStatus.Sending(42)
        val sending2 = DeliveryStatus.Sending(42)
        assertEquals(sending1, sending2)
    }

    @Test
    fun sendingInequalityWithDifferentProgress() {
        val sending1 = DeliveryStatus.Sending(42)
        val sending2 = DeliveryStatus.Sending(43)
        assert(sending1 != sending2)
    }
}
