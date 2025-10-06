package timur.gilfanov.messenger.ui.screen.user

import android.net.Uri
import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost

class ProfileEditViewModel :
    ViewModel(),
    ContainerHost<ProfileEditUiState, ProfileEditSideEffects> {
    override val container: Container<ProfileEditUiState, ProfileEditSideEffects>
        get() = TODO("Not yet implemented")

    suspend fun updatePicture(@Suppress("unused") picture: Uri): Unit = TODO("Not yet implemented")

    suspend fun removePicture(@Suppress("unused") picture: Uri): Unit = TODO("Not yet implemented")
}
