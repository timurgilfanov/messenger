package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.data.source.local.toUiLanguageOrNull
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage

data class RemoteSettings(val uiLanguage: RemoteSetting<UiLanguage>) {
    fun toDomain(): Settings = Settings(uiLanguage = uiLanguage.value)

    companion object {
        @Suppress("ReturnCount")
        fun fromItems(
            items: List<RemoteSettingItem>,
        ): ResultWithError<RemoteSettings, ParseError> {
            val uiLanguageItem = items.find { it.key == SettingKey.UI_LANGUAGE.key }

            if (uiLanguageItem == null) {
                return ResultWithError.Failure(
                    ParseError.MissingSetting(SettingKey.UI_LANGUAGE.key),
                )
            }

            val uiLanguage = uiLanguageItem.value.toUiLanguageOrNull()
                ?: return ResultWithError.Failure(
                    ParseError.InvalidValue(
                        key = SettingKey.UI_LANGUAGE.key,
                        value = uiLanguageItem.value,
                    ),
                )

            val remoteSettings = RemoteSettings(
                uiLanguage = RemoteSetting(
                    value = uiLanguage,
                    serverVersion = uiLanguageItem.version,
                ),
            )

            return ResultWithError.Success(remoteSettings)
        }
    }
}

sealed interface ParseError {
    data class MissingSetting(val key: String) : ParseError
    data class InvalidValue(val key: String, val value: String) : ParseError
}
