package timur.gilfanov.messenger.ui.screen.user

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost

class LanguageViewModel :
    ViewModel(),
    ContainerHost<LanguageUiState, LanguageSideEffects> {
    override val container: Container<LanguageUiState, LanguageSideEffects>
        get() = TODO("Not yet implemented")
}
