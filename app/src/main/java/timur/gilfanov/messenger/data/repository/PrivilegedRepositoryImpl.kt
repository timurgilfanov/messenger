package timur.gilfanov.messenger.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.local.LocalDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.privileged.PrivilegedRepository
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.privileged.RepositoryDeleteChatError

@Singleton
class PrivilegedRepositoryImpl @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val remoteDataSource: RemoteDataSource,
) : PrivilegedRepository {

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError> =
        when (val result = remoteDataSource.createChat(chat)) {
            is ResultWithError.Success -> {
                localDataSource.insertChat(result.data)
                ResultWithError.Success(result.data)
            }
            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToCreateChatError(result.error))
            }
        }

    override suspend fun deleteChat(
        chatId: ChatId,
    ): ResultWithError<Unit, RepositoryDeleteChatError> =
        when (val result = remoteDataSource.deleteChat(chatId)) {
            is ResultWithError.Success -> {
                localDataSource.deleteChat(chatId)
                ResultWithError.Success(Unit)
            }
            is ResultWithError.Failure -> {
                ResultWithError.Failure(mapRemoteErrorToDeleteChatError(result.error, chatId))
            }
        }

    private fun mapRemoteErrorToCreateChatError(
        error: RemoteDataSourceError,
    ): RepositoryCreateChatError = when (error) {
        RemoteDataSourceError.NetworkNotAvailable -> RepositoryCreateChatError.NetworkNotAvailable
        RemoteDataSourceError.ServerUnreachable -> RepositoryCreateChatError.ServerUnreachable
        RemoteDataSourceError.ServerError -> RepositoryCreateChatError.ServerError
        RemoteDataSourceError.Unauthorized -> RepositoryCreateChatError.UnknownError
        else -> RepositoryCreateChatError.UnknownError
    }

    private fun mapRemoteErrorToDeleteChatError(
        error: RemoteDataSourceError,
        chatId: ChatId,
    ): RepositoryDeleteChatError = when (error) {
        RemoteDataSourceError.NetworkNotAvailable -> RepositoryDeleteChatError.NetworkNotAvailable
        RemoteDataSourceError.ServerUnreachable -> RepositoryDeleteChatError.RemoteUnreachable
        RemoteDataSourceError.ServerError -> RepositoryDeleteChatError.RemoteError
        RemoteDataSourceError.Unauthorized -> RepositoryDeleteChatError.LocalError
        RemoteDataSourceError.ChatNotFound -> RepositoryDeleteChatError.ChatNotFound(chatId)
        else -> RepositoryDeleteChatError.LocalError
    }
}
