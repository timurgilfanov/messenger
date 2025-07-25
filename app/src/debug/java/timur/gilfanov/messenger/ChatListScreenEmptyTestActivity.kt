package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID
import timur.gilfanov.messenger.data.repository.EmptyParticipantRepository
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListUseCase
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListViewModel
import timur.gilfanov.messenger.ui.theme.MessengerTheme

class ChatListScreenEmptyTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                val emptyRepository = EmptyParticipantRepository()
                val useCase = FlowChatListUseCase(emptyRepository)
                val currentUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

                val viewModel: ChatListViewModel = viewModel {
                    ChatListViewModel(currentUserId, useCase, emptyRepository)
                }

                ChatListScreen(
                    currentUserId = ParticipantId(currentUserId),
                    actions = ChatListActions(
                        onChatClick = { chatId ->
                            // Navigation to chat screen will be implemented later
                        },
                        onNewChatClick = {
                            // Navigation to new chat screen will be implemented later
                        },
                        onSearchClick = {
                            // Navigation to search screen will be implemented later
                        },
                    ),
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
