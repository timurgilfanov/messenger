package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.domain.entity.user.UiLanguage

private val STORAGE_MAP = mapOf(
    UiLanguage.English to "English",
    UiLanguage.German to "German",
)

private val REVERSE_MAP = STORAGE_MAP.entries.associateBy({ it.value }, { it.key })

internal fun UiLanguage.toStorageValue(): String =
    STORAGE_MAP[this] ?: error("Unknown UiLanguage: $this")

internal fun String.toUiLanguageOrDefault(default: UiLanguage): UiLanguage =
    REVERSE_MAP[this] ?: default

internal fun String.toUiLanguageOrNull(): UiLanguage? = REVERSE_MAP[this]
