package timur.gilfanov.messenger.data.source.local

sealed interface GetSettingError {
    data object SettingNotFound : GetSettingError
    data object StorageUnavailable : GetSettingError
    data class UnknownError(val cause: Throwable) : GetSettingError
}

sealed interface UpdateSettingError {
    data object StorageUnavailable : UpdateSettingError
    data object StorageFull : UpdateSettingError
    data object ConcurrentModificationError : UpdateSettingError
    data class UnknownError(val cause: Throwable) : UpdateSettingError
}

sealed interface GetUnsyncedSettingsError {
    data object StorageUnavailable : GetUnsyncedSettingsError
    data class UnknownError(val cause: Throwable) : GetUnsyncedSettingsError
}
