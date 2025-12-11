package timur.gilfanov.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import timur.gilfanov.messenger.ui.screen.settings.SettingsScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class SettingsScreenTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MessengerTheme {
                SettingsScreen(
                    onProfileEditClick = {},
                    onChangeLanguageClick = {},
                    onAuthFailure = {},
                    onShowSnackbar = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
