package timur.gilfanov.messenger.ui.screen.settings

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId

@RunWith(RobolectricTestRunner::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ProfileUiMappingTest {

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @Test
    fun `maps name from Profile`() {
        val profile = Profile(testUserId, "Timur", null)

        val result = profile.toProfileUi()

        assertEquals("Timur", result.name)
    }

    @Test
    fun `maps null pictureUrl to null picture`() {
        val profile = Profile(testUserId, "Timur", null)

        val result = profile.toProfileUi()

        assertNull(result.picture)
    }

    @Test
    fun `maps pictureUrl to Uri`() {
        val profile = Profile(testUserId, "Timur", "https://example.com/pic.jpg")

        val result = profile.toProfileUi()

        val picture = assertNotNull(result.picture)
        assertEquals("https://example.com/pic.jpg", picture.toString())
    }
}
