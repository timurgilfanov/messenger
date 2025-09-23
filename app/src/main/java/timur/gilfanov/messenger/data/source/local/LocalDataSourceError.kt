package timur.gilfanov.messenger.data.source.local

sealed interface LocalDataSourceError {
    // Entity-specific errors
    data object ChatNotFound : LocalDataSourceError
    data object MessageNotFound : LocalDataSourceError
    data object ParticipantNotFound : LocalDataSourceError

    // Operation errors
    data class DuplicateEntity(
        val entityType: String, // "chat", "message", "participant"
        val identifier: String, // The ID that was duplicate
    ) : LocalDataSourceError

    data class RelatedEntityMissing(
        val missingType: String, // "participant", "chat"
        val identifier: String, // The ID that was missing
    ) : LocalDataSourceError

    // System errors
    data object StorageUnavailable : LocalDataSourceError
    data object StorageFull : LocalDataSourceError
    data object ConcurrentModificationError : LocalDataSourceError

    // Validation
    data class InvalidData(val field: String, val reason: String) : LocalDataSourceError

    // Fallback
    data class UnknownError(val cause: Throwable) : LocalDataSourceError
}
