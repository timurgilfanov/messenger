package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlin.time.Duration

sealed interface SettingsChangeBackupError {
    data object Unauthenticated : SettingsChangeBackupError
    data object InsufficientPermissions : SettingsChangeBackupError

    sealed interface ChangeNotBackedUp : SettingsChangeBackupError {
        data object NetworkNotAvailable : ChangeNotBackedUp
        data object ServiceDown : ChangeNotBackedUp
        data class Cooldown(val remaining: Duration) : ChangeNotBackedUp
        data object UnknownError : ChangeNotBackedUp
    }

    data object ChangeBackupTimeout : SettingsChangeBackupError
}
