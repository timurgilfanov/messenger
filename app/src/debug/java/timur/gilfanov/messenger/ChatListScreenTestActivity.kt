package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class ChatListScreenTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MessengerTheme {
                ChatListScreen(
                    onAuthFailure = { },
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
