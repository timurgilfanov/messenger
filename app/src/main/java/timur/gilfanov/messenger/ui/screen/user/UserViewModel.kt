package timur.gilfanov.messenger.ui.screen.user

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.user.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.user.ObserveProfileUseCase
import timur.gilfanov.messenger.domain.usecase.user.ObserveSettingsError
import timur.gilfanov.messenger.domain.usecase.user.ObserveSettingsUseCase
import timur.gilfanov.messenger.util.Logger

/**
 * ViewModel for the user profile screen.
 *
 * Displays the user's profile information (name, picture) and settings (language).
 * This is a read-only view that aggregates data from profile and settings.
 * Uses Orbit MVI pattern for state management without side effects.
 *
 * @see UserUiState for the composite state structure
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    observeProfile: ObserveProfileUseCase,
    observeSettings: ObserveSettingsUseCase,
    logger: Logger,
) : ViewModel(),
    ContainerHost<UserUiState, UserSideEffects> {

    companion object {
        private const val TAG = "UserViewModel"
    }

    // todo settings state become dependent on success of profile state. but they independent.
    //  may me profile and settings should have separate view models?
    override val container = container<UserUiState, UserSideEffects>(UserUiState.Loading) {
        coroutineScope {
            launch {
                combine(observeProfile(), observeSettings()) { profileResult, settingsResult ->
                    profileResult.fold(
                        onSuccess = { profile ->
                            val profileUi = ProfileUi(
                                name = profile.name,
                                picture = profile.pictureUrl?.toUri(),
                            )
                            settingsResult.fold(
                                onSuccess = { settings ->
                                    val settingsUi = SettingsUi(
                                        language = settings.uiLanguage,
                                    )
                                    reduce {
                                        when (val s = state) {
                                            UserUiState.Loading -> {
                                                UserUiState.Ready(
                                                    profile = profileUi,
                                                    settings = settingsUi,
                                                )
                                            }

                                            is UserUiState.Ready -> s.copy(settings = settingsUi)
                                        }
                                    }
                                },
                                onFailure = { error ->
                                    when (error) {
                                        is ObserveSettingsError.ObserveSettingsRepository -> {
                                            logger.i(
                                                TAG,
                                                "Settings observation failed with repository error: ${error.error}",
                                            )
                                            postSideEffect(
                                                UserSideEffects.GetSettingsFailed(error.error),
                                            )
                                        }

                                        ObserveSettingsError.Unauthorized -> {
                                            logger.i(
                                                TAG,
                                                "Settings observation failed with Unauthorized error",
                                            )
                                            postSideEffect(UserSideEffects.Unauthorized)
                                        }
                                    }
                                },
                            )
                        },
                        onFailure = { error ->
                            when (error) {
                                is ObserveProfileError.ObserveProfileRepository -> {
                                    logger.i(
                                        TAG,
                                        "Profile observation failed with repository error: ${error.error}",
                                    )
                                    postSideEffect(UserSideEffects.GetProfileFailed(error.error))
                                }

                                ObserveProfileError.Unauthorized -> {
                                    logger.i(
                                        TAG,
                                        "Profile observation failed with Unauthorized error",
                                    )
                                    postSideEffect(UserSideEffects.Unauthorized)
                                }
                            }
                            return@combine
                        },
                    )
                }.collect {
                }
            }
        }
    }
}
