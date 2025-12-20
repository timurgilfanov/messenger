package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.data.source.local.toUiLanguageOrNull
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.util.Logger

/**
 * Type-safe wrapper for user settings retrieved from remote server.
 *
 * Provides a typed alternative to working directly with [RemoteSettingDto] flat DTOs.
 * Each setting property is wrapped in [RemoteSetting] to handle parsing failures
 * and missing values from server gracefully.
 *
 * @property uiLanguage UI language setting with validation state
 *
 * @see fromItems Factory method that parses server response with validation
 */
data class RemoteSettings(val uiLanguage: RemoteSetting<UiLanguage>) {
    companion object {
        /**
         * Constructs RemoteSettings from server response items with validation.
         *
         * Parses each setting item and wraps it in the appropriate [RemoteSetting] state:
         * - [RemoteSetting.Valid]: Successfully parsed value
         * - [RemoteSetting.InvalidValue]: Server sent unsupported value
         * - [RemoteSetting.Missing]: Setting not present in response
         *
         * @param logger Logger for diagnostic warnings
         * @param items List of setting items from server
         * @return Typed RemoteSettings with all settings populated (using appropriate states)
         */
        fun fromItems(logger: Logger, items: List<RemoteSettingDto>): RemoteSettings {
            val uiLanguage = items.mapToRemoteSetting(
                key = SettingKey.UI_LANGUAGE,
                mapToDomainOrNull = String::toUiLanguageOrNull,
                logger = logger,
            )
            return RemoteSettings(uiLanguage = uiLanguage)
        }
    }
}

private const val TAG = "RemoteSettings"

/**
 * Maps a setting item from server response to typed RemoteSetting with validation.
 *
 * @param key Setting key to search for
 * @param mapToDomainOrNull Function to parse string value into domain type
 * @param logger Logger for diagnostic warnings
 * @return RemoteSetting in appropriate state (Valid, InvalidValue, or Missing)
 */
private fun <T> List<RemoteSettingDto>.mapToRemoteSetting(
    key: SettingKey,
    mapToDomainOrNull: String.() -> T?,
    logger: Logger,
): RemoteSetting<T> {
    val item = this.find { it.key == key.key }

    if (item == null) {
        logger.w(TAG, "Setting '${key.key}' is missing from server response")
        return RemoteSetting.Missing
    }

    val parsedValue = item.value.mapToDomainOrNull()
    return if (parsedValue == null) {
        logger.w(TAG, "Setting '${key.key}' has unsupported value '${item.value}'")
        RemoteSetting.InvalidValue(rawValue = item.value, serverVersion = item.version)
    } else {
        RemoteSetting.Valid(value = parsedValue, serverVersion = item.version)
    }
}
