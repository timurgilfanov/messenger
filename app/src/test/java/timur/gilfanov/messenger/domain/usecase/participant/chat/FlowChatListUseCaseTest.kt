package timur.gilfanov.messenger.domain.usecase.participant.chat

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented

class FlowChatListUseCaseTest {

    private class RepositoryFake(
        val chatListFlow: Flow<ResultWithError<List<Chat>, FlowChatListError>>,
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {
        override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
            chatListFlow
    }

    @Test
    fun `returns chat list updates`() = runTest {
        val chat1 = buildChat { name = "Chat 1" }
        val chat2 = buildChat { name = "Chat 2" }
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Success(listOf(chat1)))
                emit(ResultWithError.Success(listOf(chat1, chat2)))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val first = awaitItem()
            assertIs<ResultWithError.Success<List<Chat>, FlowChatListError>>(first)
            assertEquals(listOf(chat1), first.data)

            val second = awaitItem()
            assertIs<ResultWithError.Success<List<Chat>, FlowChatListError>>(second)
            assertEquals(listOf(chat1, chat2), second.data)
            awaitComplete()
        }
    }
}
