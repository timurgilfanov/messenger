package timur.gilfanov.messenger.ui.screen.user

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost

class ProfileEditViewModel :
    ViewModel(),
    ContainerHost<ProfileEditUiState, ProfileEditSideEffects> {
    override val container: Container<ProfileEditUiState, ProfileEditSideEffects>
        get() = TODO("Not yet implemented")

    fun updatePicture(): Unit = TODO("Not yet implemented")
}
