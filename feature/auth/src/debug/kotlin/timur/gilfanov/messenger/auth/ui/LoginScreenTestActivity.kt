package timur.gilfanov.messenger.auth.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timur.gilfanov.messenger.auth.ui.screen.login.LoginScreen
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@AndroidEntryPoint
class LoginScreenTestActivity : ComponentActivity() {

    @Inject
    lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessengerTheme {
                LoginScreen(
                    onNavigateToChatList = {},
                    onNavigateToSignup = {},
                    googleSignInClient = googleSignInClient,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
