package timur.gilfanov.messenger

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timur.gilfanov.messenger.domain.entity.chat.toChatId
import timur.gilfanov.messenger.domain.entity.chat.toParticipantId
import timur.gilfanov.messenger.domain.usecase.user.ObserveAndApplyLocaleUseCase
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
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var observeAndApplyLocale: ObserveAndApplyLocaleUseCase

    @Inject
    lateinit var applicationScope: CoroutineScope

//    @Inject
//    lateinit var logger: Logger

    override fun attachBaseContext(newBase: Context) {
        Log.i("MainActivity", "attachBaseContext")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = newBase.getSystemService(LocaleManager::class.java)
            val locales = localeManager.applicationLocales
            if (!locales.isEmpty) {
                val locale = locales[0]
                val configuration = newBase.resources.configuration
                configuration.setLocale(locale)
                super.attachBaseContext(newBase.createConfigurationContext(configuration))
                return
            }
        }
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            val locale = locales[0]
            val configuration = newBase.resources.configuration
            Log.i("MainActivity", "Set locale: $locale")
            configuration.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(configuration))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MainActivity", "onCreate")
        startLocaleObservation()
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                MessengerApp()
            }
        }
    }

    private fun startLocaleObservation() {
        var i = 0
        applicationScope.launch {
            observeAndApplyLocale().collect {
                if (i != 0) {
                    Log.i("MainActivity", "Recreate activity")
                    withContext(Dispatchers.Main) {
                        recreate()
                    }
                }
                i++
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
