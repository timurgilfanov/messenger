package timur.gilfanov.messenger.ui.screen.user

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import org.orbitmvi.orbit.viewmodel.container
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.user.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.user.ObserveProfileUseCase
import timur.gilfanov.messenger.util.Logger

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeProfile: ObserveProfileUseCase,
    private val logger: Logger,
) : ViewModel(),
    ContainerHost<ProfileUiState, ProfileSideEffects> {

    companion object {
        private const val TAG = "ProfileViewModel"
        private val STATE_UPDATE_DEBOUNCE = 200.milliseconds
    }

    override val container = container<ProfileUiState, ProfileSideEffects>(ProfileUiState.Loading) {
        coroutineScope {
            launch {
                observeProfileChanges()
            }
        }
    }

    @OptIn(OrbitExperimental::class, FlowPreview::class)
    private suspend fun observeProfileChanges() = subIntent {
        observeProfile()
            .debounce(STATE_UPDATE_DEBOUNCE)
            .collect { result ->
                result.fold(
                    onSuccess = { profile ->
                        reduce {
                            ProfileUiState.Ready(
                                ProfileUi(
                                    name = profile.name,
                                    picture = profile.pictureUrl?.toUri(),
                                ),
                            )
                        }
                    },
                    onFailure = { error ->
                        when (error) {
                            is ObserveProfileError.ObserveProfileRepository -> {
                                logger.i(
                                    TAG,
                                    "Profile observation failed with repository error: ${error.error}",
                                )
                                postSideEffect(ProfileSideEffects.GetProfileFailed(error.error))
                            }

                            ObserveProfileError.Unauthorized -> {
                                logger.i(
                                    TAG,
                                    "Profile observation failed with Unauthorized error",
                                )
                                postSideEffect(ProfileSideEffects.Unauthorized)
                            }
                        }
                    },
                )
            }
    }
}
