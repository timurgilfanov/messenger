package timur.gilfanov.messenger.domain.entity.user

enum class SettingKey(val key: String) {
    UI_LANGUAGE("ui_language"),
    THEME("theme"),
    NOTIFICATIONS("notifications"),
    ;

    companion object {
        fun fromKey(key: String): SettingKey? = entries.find { it.key == key }
    }
}
