package timur.gilfanov.messenger.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.auth.ui.GoogleSignInClient
import timur.gilfanov.messenger.auth.ui.screen.login.LoginScreen
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupScreen
import timur.gilfanov.messenger.domain.entity.chat.toChatId
import timur.gilfanov.messenger.navigation.Chat
import timur.gilfanov.messenger.navigation.ChatList
import timur.gilfanov.messenger.navigation.Language
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.Main
import timur.gilfanov.messenger.navigation.ProfileEdit
import timur.gilfanov.messenger.navigation.Signup
import timur.gilfanov.messenger.ui.screen.chat.ChatScreen
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.screen.main.MainScreen
import timur.gilfanov.messenger.ui.screen.settings.LanguageScreen
import timur.gilfanov.messenger.ui.screen.settings.ProfileEditScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel = hiltViewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MessengerTheme {
                MessengerApp(
                    googleSignInClient = googleSignInClient,
                    uiState = uiState,
                    effects = viewModel.effects,
                )
            }
        }
    }
}

// top-level app composable
@Suppress("LongMethod", "ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
fun MessengerApp(
    googleSignInClient: GoogleSignInClient,
    uiState: MainActivityUiState,
    effects: Flow<MainActivitySideEffect>,
) {
    when (uiState) {
        MainActivityUiState.Loading -> MessengerAppLoading()
        is MainActivityUiState.Ready -> MessengerAppReady(
            googleSignInClient = googleSignInClient,
            initialDestination = uiState.initialDestination,
            effects = effects,
        )
    }
}

@Composable
private fun MessengerAppLoading() {
    Box {}
}

@Suppress("LongMethod", "ModifierMissing", "ktlint:compose:modifier-missing-check")
@Composable
private fun MessengerAppReady(
    googleSignInClient: GoogleSignInClient,
    initialDestination: NavKey,
    effects: Flow<MainActivitySideEffect>,
) {
    val backStack = rememberNavBackStack(initialDestination)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { effect ->
                when (effect) {
                    MainActivitySideEffect.NavigateToLogin -> {
                        val top = backStack.lastOrNull()
                        if (top !is Login && top !is Signup) {
                            backStack.clear()
                            backStack.add(Login)
                        }
                    }
                }
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSavedStateNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ChatList> {
                ChatListScreen(
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
                )
            }
            entry<Main> {
                MainScreen(
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
                    onBackClick = { backStack.removeLastOrNull() },
                )
            }
            entry<Login> {
                LoginScreen(
                    onNavigateToChatList = {
                        backStack.clear()
                        backStack.add(Main)
                    },
                    onNavigateToSignup = { backStack.add(Signup) },
                    googleSignInClient = googleSignInClient,
                )
            }
            entry<Signup> {
                SignupScreen(
                    onNavigateToChatList = {
                        backStack.clear()
                        backStack.add(Main)
                    },
                    onNavigateBack = { backStack.removeLastOrNull() },
                    googleSignInClient = googleSignInClient,
                )
            }
        },
    )
}
