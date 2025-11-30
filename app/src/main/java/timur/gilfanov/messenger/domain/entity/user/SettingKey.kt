package timur.gilfanov.messenger.domain.entity.user

/**
 * Enumeration of all supported user settings.
 *
 * @property key The string identifier
 */
enum class SettingKey(val key: String) {
    /** User interface language preference */
    UI_LANGUAGE("ui_language"),

    /** Application theme (light/dark/system) */
    THEME("theme"),

    /** Notification preferences */
    NOTIFICATIONS("notifications"),
    ;

    companion object {
        /**
         * Converts a string key to its corresponding SettingKey enum value.
         *
         * @param key The string identifier
         * @return The matching SettingKey, or null if the key is not recognized
         */
        fun fromKey(key: String): SettingKey? = entries.find { it.key == key }
    }
}
