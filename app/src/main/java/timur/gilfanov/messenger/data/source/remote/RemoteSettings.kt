package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.data.source.local.toUiLanguageOrNull
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.util.Logger

data class RemoteSettings(val uiLanguage: RemoteSetting<UiLanguage>) {
    companion object {
        fun fromItems(logger: Logger, items: List<RemoteSettingItem>): RemoteSettings {
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

private fun <T> List<RemoteSettingItem>.mapToRemoteSetting(
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
