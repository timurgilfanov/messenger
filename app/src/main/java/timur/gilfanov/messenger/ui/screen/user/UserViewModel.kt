package timur.gilfanov.messenger.ui.screen.user

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost

class UserViewModel :
    ViewModel(),
    ContainerHost<UserUiState, Nothing> {
    override val container: Container<UserUiState, Nothing>
        get() = TODO("Not yet implemented")
}
