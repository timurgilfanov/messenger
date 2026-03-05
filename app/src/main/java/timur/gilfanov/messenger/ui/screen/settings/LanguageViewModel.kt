package timur.gilfanov.messenger.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.entity.settings.uiLanguageList
import timur.gilfanov.messenger.domain.usecase.settings.ChangeUiLanguageError
import timur.gilfanov.messenger.domain.usecase.settings.ChangeUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageError
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.util.Logger
import timur.gilfanov.messenger.util.repeatOnSubscription

private const val TAG = "LanguageViewModel"
private val STATE_UPDATE_DEBOUNCE = 200.milliseconds

/**
 * ViewModel for the language selection screen.
 *
 * Manages UI state and side effects for changing the user's preferred
 * application language.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val observe: ObserveUiLanguageUseCase,
    private val change: ChangeUiLanguageUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LanguageUiState(
            languages = uiLanguageList,
            selectedLanguage = null,
        ),
    )
    val state = _state.asStateFlow()

    private val _effects = Channel<LanguageSideEffects>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /* Channel rather than MutableStateFlow: MutableStateFlow deduplicates by equality,
       so retrying a failed change with the same language would be silently dropped.
       CONFLATED enforces last-write-wins: a new tap while a write is in-flight
       overwrites the buffered next language rather than queuing behind it */
    private val changeLanguageChannel = Channel<UiLanguage>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            _state.repeatOnSubscription {
                observeLanguageChanges()
            }
        }

        viewModelScope.launch {
            changeLanguageChannel.consumeAsFlow().collectLatest { value ->
                change(value).fold(
                    onSuccess = {
                        logger.i(TAG, "Language changed to $value")
                    },
                    onFailure = { error ->
                        when (error) {
                            ChangeUiLanguageError.Unauthorized -> {
                                logger.i(TAG, "Language change failed with Unauthorized error")
                                _effects.send(LanguageSideEffects.Unauthorized)
                            }

                            is ChangeUiLanguageError.LocalOperationFailed -> {
                                logger.i(
                                    TAG,
                                    "Language change failed with local error: ${error.error}",
                                )
                                _effects.send(LanguageSideEffects.ChangeFailed(error.error))
                            }
                        }
                    },
                )
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeLanguageChanges() {
        observe()
            .debounce(STATE_UPDATE_DEBOUNCE)
            .collect { result ->
                result.fold(
                    onSuccess = { language ->
                        _state.update { it.copy(selectedLanguage = language) }
                    },
                    onFailure = { error ->
                        when (error) {
                            ObserveUiLanguageError.Unauthorized -> {
                                logger.i(TAG, "Language observation failed with Unauthorized error")
                                _effects.send(LanguageSideEffects.Unauthorized)
                            }

                            ObserveUiLanguageError.SettingsResetToDefaults -> {
                                logger.i(TAG, "Settings were reset to defaults")
                            }

                            is ObserveUiLanguageError.LocalOperationFailed -> {
                                logger.i(
                                    TAG,
                                    "Language observation failed with local error: ${error.error}",
                                )
                            }
                        }
                    },
                )
            }
    }

    /*
    This function change UI language for application.

    Business requirement: language change must have last write wins ordering.
     */
    fun changeLanguage(value: UiLanguage) {
        changeLanguageChannel.trySend(value)
    }
}
