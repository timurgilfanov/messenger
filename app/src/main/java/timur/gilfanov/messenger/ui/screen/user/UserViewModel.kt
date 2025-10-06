package timur.gilfanov.messenger.ui.screen.user

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost

/**
 * ViewModel for the user profile screen.
 *
 * Displays the user's profile information (name, picture) and settings (language).
 * This is a read-only view that aggregates data from profile and settings.
 * Uses Orbit MVI pattern for state management without side effects.
 *
 * @see UserUiState for the composite state structure
 */
class UserViewModel :
    ViewModel(),
    ContainerHost<UserUiState, Nothing> {
    override val container: Container<UserUiState, Nothing>
        get() = TODO("Not yet implemented")
}
