package timur.gilfanov.messenger.data.source.local

sealed interface GetSettingError {
    data object SettingNotFound : GetSettingError

    data object ConcurrentModificationError : GetSettingError
    data object DiskIOError : GetSettingError

    data object DatabaseCorrupted : GetSettingError
    data object AccessDenied : GetSettingError
    data object ReadOnlyDatabase : GetSettingError

    data class UnknownError(val cause: Throwable) : GetSettingError
}

sealed interface UpdateSettingError {
    data object SettingsNotFound : UpdateSettingError

    data object ConcurrentModificationError : UpdateSettingError
    data object DiskIOError : UpdateSettingError

    data object StorageFull : UpdateSettingError
    data object DatabaseCorrupted : UpdateSettingError
    data object AccessDenied : UpdateSettingError
    data object ReadOnlyDatabase : UpdateSettingError

    data class UnknownError(val cause: Throwable) : UpdateSettingError
}

sealed interface GetUnsyncedSettingsError {
    data object ConcurrentModificationError : GetUnsyncedSettingsError
    data object DiskIOError : GetUnsyncedSettingsError

    data object DatabaseCorrupted : GetUnsyncedSettingsError
    data object AccessDenied : GetUnsyncedSettingsError
    data object ReadOnlyDatabase : GetUnsyncedSettingsError

    data class UnknownError(val cause: Throwable) : GetUnsyncedSettingsError
}
