package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.chat.toChatId
import timur.gilfanov.messenger.domain.entity.chat.toParticipantId
import timur.gilfanov.messenger.navigation.Chat
import timur.gilfanov.messenger.navigation.ChatList
import timur.gilfanov.messenger.navigation.Language
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.Main
import timur.gilfanov.messenger.navigation.ProfileEdit
import timur.gilfanov.messenger.ui.screen.chat.ChatScreen
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.screen.main.MainScreen
import timur.gilfanov.messenger.ui.screen.settings.LanguageScreen
import timur.gilfanov.messenger.ui.screen.settings.LoginScreen
import timur.gilfanov.messenger.ui.screen.settings.ProfileEditScreen
import timur.gilfanov.messenger.ui.screen.settings.ProfileEditViewModel
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                MessengerApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Suppress("LongMethod") // todo keep entities in feature modules
@Composable
fun MessengerApp(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    val currentUserId = "550e8400-e29b-41d4-a716-446655440000".toParticipantId()

    val initBackStack = if (BuildConfig.FEATURE_SETTINGS) Main else ChatList
    val backStack = rememberNavBackStack(initBackStack)

    val onAuthFailure: () -> Unit = {
        backStack.clear()
        backStack.add(Login)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val onShowSnackbar: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
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
                    modifier = modifier,
                )
            }
            entry<Chat> { chat ->
                ChatScreen(
                    chatId = chat.chatId.toChatId(),
                    currentUserId = currentUserId,
                    modifier = modifier,
                )
            }
            entry<Main> {
                MainScreen(
                    snackbarHostState = snackbarHostState,
                    currentUserId = currentUserId,
                    onAuthFailure = onAuthFailure,
                    onShowSnackbar = onShowSnackbar,
                    onChatClick = { chatId -> backStack.add(Chat(chatId.id.toString())) },
                    onNewChatClick = {
                        // Navigation to new chat screen will be implemented later
                    },
                    onSearchClick = {
                        // Navigation to search screen will be implemented later
                    },
                    onProfileEditClick = { backStack.add(ProfileEdit) },
                    onChangeLanguageClick = { backStack.add(Language) },
                    modifier = modifier,
                )
            }
            entry<ProfileEdit> {
                ProfileEditScreen(
                    onDoneClick = { backStack.removeLastOrNull() },
                    modifier = modifier,
                    viewModel = ProfileEditViewModel(),
                )
            }
            entry<Language> {
                LanguageScreen(
                    onAuthFailure = onAuthFailure,
                    onShowSnackbar = onShowSnackbar,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = modifier,
                )
            }
            entry<Login> {
                LoginScreen()
            }
        },
    )
}
