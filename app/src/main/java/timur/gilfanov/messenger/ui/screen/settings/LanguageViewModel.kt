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
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.entity.settings.uiLanguageList
import timur.gilfanov.messenger.domain.usecase.settings.ChangeUiLanguageError
import timur.gilfanov.messenger.domain.usecase.settings.ChangeUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.util.Logger

/**
 * ViewModel for the language selection screen.
 *
 * Manages UI state and side effects for changing the user's preferred
 * application language. Uses Orbit MVI pattern for state management.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val observe: ObserveUiLanguageUseCase,
    private val change: ChangeUiLanguageUseCase,
    private val logger: Logger,
) : ViewModel(),
    ContainerHost<LanguageUiState, LanguageSideEffects> {

    companion object {
        private const val TAG = "LanguageViewModel"
        private val STATE_UPDATE_DEBOUNCE = 200.milliseconds
    }

    override val container = container<LanguageUiState, LanguageSideEffects>(
        LanguageUiState(
            languages = uiLanguageList,
            selectedLanguage = null,
        ),
    ) {
        coroutineScope {
            launch {
                observeLanguageChanges()
            }
        }
    }

    @OptIn(OrbitExperimental::class, FlowPreview::class)
    private suspend fun observeLanguageChanges() = subIntent {
        observe()
            .debounce(STATE_UPDATE_DEBOUNCE)
            .collect { result ->
                result.fold(
                    onSuccess = { language ->
                        reduce {
                            state.copy(selectedLanguage = language)
                        }
                    },
                    onFailure = { error ->
                        when (error) {
                            is ObserveUiLanguageError.ObserveLanguageRepository -> {
                                logger.i(
                                    TAG,
                                    "Language observation failed with repository error: ${error.error}",
                                )
                            }

                            ObserveUiLanguageError.Unauthorized -> {
                                logger.i(TAG, "Language observation failed with Unauthorized error")
                                postSideEffect(
                                    LanguageSideEffects.Unauthorized,
                                )
                            }
                        }
                    },
                )
            }
    }

    /**
     * Changes the user's UI language preference.
     *
     * @param value The language item to set as the new preference
     */
    fun changeLanguage(value: UiLanguage) {
        intent {
            change(value).fold(
                onSuccess = {
                    logger.i(TAG, "Language changed to $value")
                },
                onFailure = { error ->
                    when (error) {
                        is ChangeUiLanguageError.ChangeLanguageRepository -> {
                            logger.i(
                                TAG,
                                "Language change failed with repository error: ${error.error}",
                            )
                            postSideEffect(LanguageSideEffects.ChangeFailed(error.error))
                        }

                        ChangeUiLanguageError.Unauthorized -> {
                            logger.i(TAG, "Language change failed with Unauthorized error")
                            postSideEffect(
                                LanguageSideEffects.Unauthorized,
                            )
                        }
                    }
                },
            )
        }
    }
}
