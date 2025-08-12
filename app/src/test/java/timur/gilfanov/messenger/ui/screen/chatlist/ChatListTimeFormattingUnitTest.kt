package timur.gilfanov.messenger.ui.screen.chatlist

import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit

@Category(Unit::class)
class ChatListTimeFormattingUnitTest {

    @Test
    fun `getDaysDifference returns 0 for same day`() {
        val now = Calendar.getInstance()
        val sameDay = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
        }

        val result = getDaysDifference(now, sameDay)

        assertEquals(0, result)
    }

    @Test
    fun `getDaysDifference returns 1 for yesterday`() {
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis - 24.hours.inWholeMilliseconds
        }

        val result = getDaysDifference(now, yesterday)

        assertEquals(1, result)
    }

    @Test
    fun `getDaysDifference returns correct value for multiple days`() {
        val now = Calendar.getInstance()
        val threeDaysAgo = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis - (3 * 24.hours.inWholeMilliseconds)
        }

        val result = getDaysDifference(now, threeDaysAgo)

        assertEquals(3, result)
    }

    @Test
    fun `getDaysDifference handles year boundary correctly`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.DAY_OF_YEAR, 1) // January 1st
        }
        val lastYearEnd = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2023)
            set(Calendar.DAY_OF_YEAR, 365) // December 31st
        }

        val result = getDaysDifference(now, lastYearEnd)

        assertEquals(1, result) // 1 day difference between Dec 31 and Jan 1
    }

    @Test
    fun `getDaysDifference handles leap year correctly`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024) // Leap year
            set(Calendar.DAY_OF_YEAR, 1)
        }
        val lastYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2023)
            set(Calendar.DAY_OF_YEAR, 1)
        }

        val result = getDaysDifference(now, lastYear)

        assertEquals(365, result) // Exactly one year difference
    }
}
