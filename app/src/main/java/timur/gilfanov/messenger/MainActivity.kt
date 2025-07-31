package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import kotlinx.serialization.Serializable
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.ui.screen.chat.ChatScreen
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Serializable
data object ChatList : NavKey

@Serializable
data class Chat(val chatId: String) : NavKey

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                val backStack = rememberNavBackStack(ChatList)
                val currentUserId = ParticipantId(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                )

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<ChatList> {
                            ChatListScreen(
                                currentUserId = currentUserId,
                                actions = ChatListActions(
                                    onChatClick = { chatId ->
                                        backStack.add(Chat(chatId.id.toString()))
                                    },
                                    onNewChatClick = {
                                        // Navigation to new chat screen will be implemented later
                                    },
                                    onSearchClick = {
                                        // Navigation to search screen will be implemented later
                                    },
                                ),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        entry<Chat> { chat ->
                            ChatScreen(
                                chatId = ChatId(UUID.fromString(chat.chatId)),
                                currentUserId = currentUserId,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                )
            }
        }
    }
}
