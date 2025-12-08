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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.navigation.Language
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.ProfileEdit
import timur.gilfanov.messenger.navigation.Settings
import timur.gilfanov.messenger.ui.screen.settings.LanguageScreen
import timur.gilfanov.messenger.ui.screen.settings.LoginScreen
import timur.gilfanov.messenger.ui.screen.settings.ProfileEditScreen
import timur.gilfanov.messenger.ui.screen.settings.ProfileEditViewModel
import timur.gilfanov.messenger.ui.screen.settings.SettingsScreen

@Suppress("LongMethod") // remove suppression on implementation stage
@Composable
fun MainScreen(
    chatNavDisplay: @Composable () -> Unit,
    @Suppress("unused") modifier: Modifier = Modifier, // really not needed
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val settingsBackStack = rememberNavBackStack(Settings)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            0 -> chatNavDisplay()

            1 -> NavDisplay(
                backStack = settingsBackStack,
                onBack = { settingsBackStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Settings> {
                        SettingsScreen(
                            onProfileEditClick = { settingsBackStack.add(ProfileEdit) },
                            onChangeLanguageClick = { settingsBackStack.add(Language) },
                            modifier = defaultModifier,
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
                            onAuthFailure = {
                                settingsBackStack.clear()
                                settingsBackStack.add(Login)
                            },
                            onShowSnackbar = { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            },
                            onBackClick = { settingsBackStack.removeLastOrNull() },
                            modifier = defaultModifier,
                        )
                    }
                    entry<Login> {
                        LoginScreen()
                    }
                },
            )
        }
    }
}
