package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

/**
 * Bidirectional mapping between [UiLanguage] domain types and database storage strings.
 *
 * ## Storage Format:
 * - English → "English"
 * - German → "German"
 *
 * The string format is case-sensitive and used for both Room database storage
 * and network communication with the server.
 */

private val STORAGE_MAP = mapOf(
    UiLanguage.English to "English",
    UiLanguage.German to "German",
)

private val REVERSE_MAP = STORAGE_MAP.entries.associateBy({ it.value }, { it.key })

/**
 * Converts UiLanguage to its database storage string representation.
 *
 * @return Storage string (e.g., "English", "German")
 * @throws IllegalStateException if UiLanguage enum has unexpected value
 */
internal fun UiLanguage.toStorageValue(): String =
    STORAGE_MAP[this] ?: error("Unknown UiLanguage: $this")

/**
 * Parses a storage string to UiLanguage, falling back to default if parsing fails.
 *
 * @param default The fallback value if string doesn't match any known language
 * @return Parsed UiLanguage or the default value
 */
internal fun String.toUiLanguageOrDefault(default: UiLanguage): UiLanguage =
    REVERSE_MAP[this] ?: default

/**
 * Parses a storage string to UiLanguage, returning null if parsing fails.
 *
 * Used for validation without fallback behavior.
 *
 * @return Parsed UiLanguage or null if string doesn't match any known language
 */
internal fun String.toUiLanguageOrNull(): UiLanguage? = REVERSE_MAP[this]
