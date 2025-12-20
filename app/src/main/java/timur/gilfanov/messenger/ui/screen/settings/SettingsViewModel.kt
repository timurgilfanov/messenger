package timur.gilfanov.messenger.ui.screen.settings

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
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveSettingsUseCase
import timur.gilfanov.messenger.util.Logger

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val logger: Logger,
) : ViewModel(),
    ContainerHost<SettingsUiState, SettingsSideEffects> {

    companion object {
        private const val TAG = "SettingsViewModel"
        private val STATE_UPDATE_DEBOUNCE = 200.milliseconds
    }

    override val container =
        container<SettingsUiState, SettingsSideEffects>(SettingsUiState.Loading) {
            coroutineScope {
                launch {
                    observeSettingsChanges()
                }
            }
        }

    @OptIn(OrbitExperimental::class, FlowPreview::class)
    private suspend fun observeSettingsChanges() = subIntent {
        observeSettings()
            .debounce(STATE_UPDATE_DEBOUNCE)
            .collect { result ->
                result.fold(
                    onSuccess = { settings ->
                        reduce {
                            SettingsUiState.Ready(settings.toSettingsUi())
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
                                    SettingsSideEffects.ObserveSettingsFailed(error.error),
                                )
                            }

                            ObserveSettingsError.Unauthorized -> {
                                logger.i(
                                    TAG,
                                    "Settings observation failed with Unauthorized error",
                                )
                                postSideEffect(SettingsSideEffects.Unauthorized)
                            }
                        }
                    },
                )
            }
    }
}
