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

sealed interface UpsertSettingError {
    data object SettingsNotFound : UpsertSettingError

    data object ConcurrentModificationError : UpsertSettingError
    data object DiskIOError : UpsertSettingError

    data object StorageFull : UpsertSettingError
    data object DatabaseCorrupted : UpsertSettingError
    data object AccessDenied : UpsertSettingError
    data object ReadOnlyDatabase : UpsertSettingError

    data class UnknownError(val cause: Throwable) : UpsertSettingError
}

sealed interface GetUnsyncedSettingsError {
    data object ConcurrentModificationError : GetUnsyncedSettingsError
    data object DiskIOError : GetUnsyncedSettingsError

    data object DatabaseCorrupted : GetUnsyncedSettingsError
    data object AccessDenied : GetUnsyncedSettingsError
    data object ReadOnlyDatabase : GetUnsyncedSettingsError

    data class UnknownError(val cause: Throwable) : GetUnsyncedSettingsError
}
