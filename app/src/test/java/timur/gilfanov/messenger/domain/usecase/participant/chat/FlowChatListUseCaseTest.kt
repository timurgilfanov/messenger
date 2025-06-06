package timur.gilfanov.messenger.domain.usecase.participant.chat

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented

class FlowChatListUseCaseTest {

    private class RepositoryFake(
        val chatListFlow: Flow<List<Chat>>,
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {
        override suspend fun flowChatList(): Flow<List<Chat>> = chatListFlow
    }

    @Test
    fun `returns chat list updates`() = runTest {
        val chat1 = buildChat { name = "Chat 1" }
        val chat2 = buildChat { name = "Chat 2" }
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(listOf(chat1))
                emit(listOf(chat1, chat2))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            assertEquals(listOf(chat1), awaitItem())
            assertEquals(listOf(chat1, chat2), awaitItem())
            awaitComplete()
        }
    }
}
