package timur.gilfanov.messenger.ui.screen.settings

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.domain.entity.profile.Profile

@RunWith(RobolectricTestRunner::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ProfileUiMappingTest {

    @Test
    fun `maps name from Profile`() {
        val profile = Profile("Timur", null)

        val result = profile.toProfileUi()

        assertEquals("Timur", result.name)
    }

    @Test
    fun `maps null pictureUrl to null picture`() {
        val profile = Profile("Timur", null)

        val result = profile.toProfileUi()

        assertNull(result.picture)
    }

    @Test
    fun `maps pictureUrl to Uri`() {
        val profile = Profile("Timur", "https://example.com/pic.jpg")

        val result = profile.toProfileUi()

        val picture = assertNotNull(result.picture)
        assertEquals("https://example.com/pic.jpg", picture.toString())
    }
}
