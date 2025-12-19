package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import timur.gilfanov.messenger.domain.entity.chat.toChatId
import timur.gilfanov.messenger.domain.entity.chat.toParticipantId
import timur.gilfanov.messenger.navigation.Chat
import timur.gilfanov.messenger.navigation.ChatList
import timur.gilfanov.messenger.navigation.Language
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.Main
import timur.gilfanov.messenger.navigation.ProfileEdit
import timur.gilfanov.messenger.ui.activity.MainActivityViewModel
import timur.gilfanov.messenger.ui.screen.chat.ChatScreen
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.screen.main.MainScreen
import timur.gilfanov.messenger.ui.screen.settings.LanguageScreen
import timur.gilfanov.messenger.ui.screen.settings.LoginScreen
import timur.gilfanov.messenger.ui.screen.settings.ProfileEditScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

// todo move activity to ui.activity package
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            hiltViewModel<MainActivityViewModel>() // needed only for locale observation

            MessengerTheme {
                MessengerApp()
            }
        }
    }
}

@Suppress("LongMethod") // todo keep entities in feature modules
@Composable
fun MessengerApp() {
    val currentUserId = "550e8400-e29b-41d4-a716-446655440000".toParticipantId()

    @Suppress("KotlinConstantConditions")
    val initBackStack = if (BuildConfig.FEATURE_SETTINGS) Main else ChatList
    val backStack = rememberNavBackStack(initBackStack)

    val onAuthFailure: () -> Unit = {
        backStack.clear()
        backStack.add(Login)
    }

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
                )
            }
            entry<Chat> { chat ->
                ChatScreen(
                    chatId = chat.chatId.toChatId(),
                    currentUserId = currentUserId,
                )
            }
            entry<Main> {
                MainScreen(
                    currentUserId = currentUserId,
                    onAuthFailure = onAuthFailure,
                    onChatClick = { chatId -> backStack.add(Chat(chatId.id.toString())) },
                    onNewChatClick = {
                        // Navigation to new chat screen will be implemented later
                    },
                    onSearchClick = {
                        // Navigation to search screen will be implemented later
                    },
                    onProfileEditClick = { backStack.add(ProfileEdit) },
                    onChangeLanguageClick = { backStack.add(Language) },
                )
            }
            entry<ProfileEdit> {
                ProfileEditScreen(
                    onDoneClick = { backStack.removeLastOrNull() },
                )
            }
            entry<Language> {
                LanguageScreen(
                    onAuthFailure = onAuthFailure,
                    onBackClick = { backStack.removeLastOrNull() },
                )
            }
            entry<Login> {
                LoginScreen()
            }
        },
    )
}
