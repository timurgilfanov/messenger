package timur.gilfanov.messenger.ui.screen.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost

/**
 * Store for the profile editing screen.
 *
 * Manages user profile updates including display name and profile picture changes.
 * Uses Orbit MVI pattern for state management with [ProfileEditUiState] for UI state
 * and [ProfileEditSideEffects] for one-time events.
 *
 * ## Error Handling Strategy
 *
 * This Store implements a dual error handling approach:
 * - **State errors**: Persistent errors shown inline (validation, upload progress)
 * - **Side effect errors**: Transient notifications (instant operation failures)
 *
 * @see ProfileEditUiState for detailed error handling documentation
 * @see ProfileEditSideEffects for side effect patterns
 */
class ProfileEditStore :
    ViewModel(),
    ContainerHost<ProfileEditUiState, ProfileEditSideEffects> {
    override val container: Container<ProfileEditUiState, ProfileEditSideEffects>
        get() = TODO("Not yet implemented")

    /**
     * Updates the user's profile picture.
     *
     * Uploads the new picture and tracks progress in [ProfileEditUiState.pictureUpdatingProgress].
     * Errors are stored in state ([ProfileEditUiState.pictureUpdatingError]) since upload
     * is a long-running operation requiring retry capability.
     *
     * @param picture URI of the new profile picture selected by the user
     */
    suspend fun updatePicture(@Suppress("unused") picture: Uri): Unit = TODO("Not yet implemented")

    /**
     * Removes the user's profile picture.
     *
     * This is an instant operation, so errors are emitted as side effects
     * ([ProfileEditSideEffects.PictureRemovingFailed]) rather than stored in state.
     *
     * @param picture URI of the current profile picture to remove
     */
    suspend fun removePicture(@Suppress("unused") picture: Uri): Unit = TODO("Not yet implemented")
}
