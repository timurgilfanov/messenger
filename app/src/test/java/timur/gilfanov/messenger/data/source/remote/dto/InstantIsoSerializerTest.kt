package timur.gilfanov.messenger.data.source.remote.dto

import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit

@Serializable
data class TestData(
    @Serializable(with = InstantIsoSerializer::class)
    val timestamp: Instant,
)

@Category(Unit::class)
class InstantIsoSerializerTest {

    private val json = Json {
        prettyPrint = false
    }

    companion object {
        private val TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z")
    }

    @Test
    fun `serialize should convert Instant to ISO 8601 string`() {
        val testData = TestData(timestamp = TEST_TIMESTAMP)

        val jsonString = json.encodeToString(testData)

        assertEquals("""{"timestamp":"2024-01-15T10:30:00Z"}""", jsonString)
    }

    @Test
    fun `deserialize should parse ISO 8601 string to Instant`() {
        val jsonString = """{"timestamp":"2024-01-15T10:30:00Z"}"""

        val testData = json.decodeFromString<TestData>(jsonString)

        assertEquals(TEST_TIMESTAMP, testData.timestamp)
    }

    @Test
    fun `round-trip serialization should preserve value`() {
        val original = TestData(timestamp = TEST_TIMESTAMP)

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<TestData>(jsonString)

        assertEquals(original.timestamp, deserialized.timestamp)
    }
}
