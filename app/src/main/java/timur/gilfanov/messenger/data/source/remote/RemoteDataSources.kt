package timur.gilfanov.messenger.data.source.remote

/**
 * Facade that groups all remote data source interfaces together.
 * Simplifies dependency injection by reducing the number of constructor parameters.
 */
data class RemoteDataSources(
    val chat: RemoteChatDataSource,
    val message: RemoteMessageDataSource,
    val sync: RemoteSyncDataSource,
)
