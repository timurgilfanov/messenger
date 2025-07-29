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

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_CURRENT_USER_ID = "extra_current_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = ChatId(UUID.fromString(intent.getStringExtra(EXTRA_CHAT_ID)))

        val currentUserId = ParticipantId(
            UUID.fromString(intent.getStringExtra(EXTRA_CURRENT_USER_ID)),
        )

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
