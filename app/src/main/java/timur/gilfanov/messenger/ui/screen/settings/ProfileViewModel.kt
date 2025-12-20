package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.util.Logger

@Suppress("UnusedPrivateProperty")
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeProfile: ObserveProfileUseCase,
    private val logger: Logger,
) : ViewModel(),
    ContainerHost<ProfileUiState, ProfileSideEffects> {

    override val container = container<ProfileUiState, ProfileSideEffects>(ProfileUiState.Loading)
}
