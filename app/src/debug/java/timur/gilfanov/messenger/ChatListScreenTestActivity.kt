package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import timur.gilfanov.messenger.di.TestUserId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class ChatListScreenTestActivity : ComponentActivity() {

    @Inject
    @TestUserId
    lateinit var userIdString: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentUserId = ParticipantId(UUID.fromString(userIdString))

        setContent {
            MessengerTheme {
                ChatListScreen(
                    currentUserId = currentUserId,
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
