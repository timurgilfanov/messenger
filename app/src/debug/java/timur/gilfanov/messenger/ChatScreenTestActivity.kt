package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.ui.screen.chat.ChatScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class ChatScreenTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                ChatScreen(
                    chatId = ChatId(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                    ),
                    currentUserId = ParticipantId(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
