package timur.gilfanov.messenger.ui.screen.user

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost

/**
 * ViewModel for the language selection screen.
 *
 * Manages UI state and side effects for changing the user's preferred
 * application language. Uses Orbit MVI pattern for state management.
 */
class LanguageViewModel :
    ViewModel(),
    ContainerHost<LanguageUiState, LanguageSideEffects> {
    override val container: Container<LanguageUiState, LanguageSideEffects>
        get() = TODO("Not yet implemented")

    /**
     * Changes the user's UI language preference.
     *
     * @param value The language item to set as the new preference
     */
    fun changeLanguage(@Suppress("unused") value: LanguageItem): Unit = TODO("Not yet implemented")
}
