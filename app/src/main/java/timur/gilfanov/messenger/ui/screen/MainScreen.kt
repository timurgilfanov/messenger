package timur.gilfanov.messenger.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import java.util.UUID
import timur.gilfanov.messenger.Chat
import timur.gilfanov.messenger.ChatList
import timur.gilfanov.messenger.Language
import timur.gilfanov.messenger.ProfileEdit
import timur.gilfanov.messenger.Settings
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.ui.screen.chat.ChatScreen
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.screen.user.LanguageScreen
import timur.gilfanov.messenger.ui.screen.user.LanguageViewModel
import timur.gilfanov.messenger.ui.screen.user.ProfileEditScreen
import timur.gilfanov.messenger.ui.screen.user.ProfileEditViewModel
import timur.gilfanov.messenger.ui.screen.user.UserScreen
import timur.gilfanov.messenger.ui.screen.user.UserViewModel

@Suppress("LongMethod") // remove suppression on implementation stage
@Composable
fun MainScreen(
    chatsBackStack: NavBackStack,
    onChatClick: (String) -> Unit,
    currentUserId: ParticipantId,
    @Suppress("unused") modifier: Modifier = Modifier, // really not needed
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val settingsBackStack = rememberNavBackStack(Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                    label = { Text("Chats") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { paddingValues ->
        val defaultModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        when (selectedTab) {
            0 -> NavDisplay(
                backStack = chatsBackStack,
                onBack = { chatsBackStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<ChatList> {
                        ChatListScreen(
                            currentUserId = currentUserId,
                            actions = ChatListActions(
                                onChatClick = { chatId ->
                                    onChatClick(chatId.id.toString())
                                },
                                onNewChatClick = {},
                                onSearchClick = {},
                            ),
                            modifier = defaultModifier,
                        )
                    }
                    entry<Chat> { chat ->
                        ChatScreen(
                            chatId = ChatId(UUID.fromString(chat.chatId)),
                            currentUserId = currentUserId,
                            modifier = defaultModifier,
                        )
                    }
                },
            )

            1 -> NavDisplay(
                backStack = settingsBackStack,
                onBack = { settingsBackStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Settings> {
                        UserScreen(
                            onProfileEditClick = { settingsBackStack.add(ProfileEdit) },
                            onChangeLanguageClick = { settingsBackStack.add(Language) },
                            modifier = defaultModifier,
                            viewModel = UserViewModel(),
                        )
                    }
                    entry<ProfileEdit> {
                        ProfileEditScreen(
                            onDoneClick = { settingsBackStack.removeLastOrNull() },
                            modifier = defaultModifier,
                            viewModel = ProfileEditViewModel(),
                        )
                    }
                    entry<Language> {
                        LanguageScreen(
                            modifier = defaultModifier,
                            viewModel = LanguageViewModel(),
                        )
                    }
                },
            )
        }
    }
}
