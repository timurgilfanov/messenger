package timur.gilfanov.messenger.ui.screen.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListActions
import timur.gilfanov.messenger.ui.screen.chatlist.ChatListScreen
import timur.gilfanov.messenger.ui.screen.settings.SettingsScreen

@Suppress("LongParameterList") // in Compose property drilling is preferred over wrapper
@Composable
fun MainScreen(
    onAuthFailure: () -> Unit,
    onProfileEditClick: () -> Unit,
    onChangeLanguageClick: () -> Unit,
    onChatClick: (ChatId) -> Unit,
    onNewChatClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    store: MainScreenStore = hiltViewModel(),
) {
    val uiState by store.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onShowSnackbar: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_nav")) {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { store.selectTab(0) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                    label = { Text(stringResource(R.string.main_tab_chats)) },
                    modifier = Modifier.testTag("bottom_nav_chats"),
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { store.selectTab(1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.main_tab_settings)) },
                    modifier = Modifier.testTag("bottom_nav_settings"),
                )
            }
        },
    ) { paddingValues ->
        val modifier1 = Modifier.padding(paddingValues)
        when (uiState.selectedTab) {
            0 -> ChatListScreen(
                actions = ChatListActions(
                    onChatClick = onChatClick,
                    onNewChatClick = onNewChatClick,
                    onSearchClick = onSearchClick,
                ),
                modifier = modifier1,
            )

            1 -> SettingsScreen(
                onProfileEditClick = onProfileEditClick,
                onChangeLanguageClick = onChangeLanguageClick,
                onAuthFailure = onAuthFailure,
                onShowSnackbar = onShowSnackbar,
                modifier = modifier1,
            )
        }
    }
}
