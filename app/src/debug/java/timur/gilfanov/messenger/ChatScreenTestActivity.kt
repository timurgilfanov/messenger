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
import timur.gilfanov.messenger.di.TestChatId
import timur.gilfanov.messenger.di.TestUserId
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.ui.screen.chat.ChatScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class ChatScreenTestActivity : ComponentActivity() {

    @Inject
    @TestUserId
    lateinit var userIdString: String

    @Inject
    @TestChatId
    lateinit var chatIdString: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentUserId = ParticipantId(UUID.fromString(userIdString))
        val chatId = ChatId(UUID.fromString(chatIdString))

        setContent {
            MessengerTheme {
                ChatScreen(
                    chatId = chatId,
                    currentUserId = currentUserId,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
