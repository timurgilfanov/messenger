package timur.gilfanov.messenger.data.source.local

/**
 * Facade that groups all local data source interfaces together.
 * Simplifies dependency injection by reducing the number of constructor parameters.
 */
data class LocalDataSources(
    val chat: LocalChatDataSource,
    val message: LocalMessageDataSource,
    val sync: LocalSyncDataSource,
)
