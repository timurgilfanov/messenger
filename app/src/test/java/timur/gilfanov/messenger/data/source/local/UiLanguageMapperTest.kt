package timur.gilfanov.messenger.data.source.local

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

@Category(Unit::class)
class UiLanguageMapperTest {

    @Test
    fun `toStorageValue converts English to correct string`() {
        // When
        val result = UiLanguage.English.toStorageValue()

        // Then
        assertEquals("English", result)
    }

    @Test
    fun `toStorageValue converts German to correct string`() {
        // When
        val result = UiLanguage.German.toStorageValue()

        // Then
        assertEquals("German", result)
    }

    @Test
    fun `toUiLanguageOrNull returns English for valid string`() {
        // When
        val result = "English".toUiLanguageOrNull()

        // Then
        assertEquals(UiLanguage.English, result)
    }

    @Test
    fun `toUiLanguageOrNull returns German for valid string`() {
        // When
        val result = "German".toUiLanguageOrNull()

        // Then
        assertEquals(UiLanguage.German, result)
    }

    @Test
    fun `toUiLanguageOrNull returns null for invalid string`() {
        // When
        val result = "French".toUiLanguageOrNull()

        // Then
        assertNull(result)
    }

    @Test
    fun `toUiLanguageOrNull returns null for empty string`() {
        // When
        val result = "".toUiLanguageOrNull()

        // Then
        assertNull(result)
    }

    @Test
    fun `toUiLanguageOrDefault returns value for valid string`() {
        // When
        val result = "German".toUiLanguageOrDefault(UiLanguage.English)

        // Then
        assertEquals(UiLanguage.German, result)
    }

    @Test
    fun `toUiLanguageOrDefault returns default for invalid string`() {
        // When
        val result = "Spanish".toUiLanguageOrDefault(UiLanguage.English)

        // Then
        assertEquals(UiLanguage.English, result)
    }

    @Test
    fun `toUiLanguageOrDefault is case sensitive`() {
        // When
        val result = "english".toUiLanguageOrDefault(UiLanguage.German)

        // Then
        assertEquals(UiLanguage.German, result, "Should return default for incorrect case")
    }

    @Test
    fun `bidirectional conversion preserves English value`() {
        // Given
        val original = UiLanguage.English

        // When
        val storageValue = original.toStorageValue()
        val restored = storageValue.toUiLanguageOrNull()

        // Then
        assertEquals(original, restored)
    }

    @Test
    fun `bidirectional conversion preserves German value`() {
        // Given
        val original = UiLanguage.German

        // When
        val storageValue = original.toStorageValue()
        val restored = storageValue.toUiLanguageOrNull()

        // Then
        assertEquals(original, restored)
    }
}
