package timur.gilfanov.messenger.domain.usecase.user.repository

sealed interface SyncLocalToRemoteRepositoryError {
    data object SettingsNotSynced : SyncLocalToRemoteRepositoryError
}
