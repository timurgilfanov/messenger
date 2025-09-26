package timur.gilfanov.messenger.debug.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import org.orbitmvi.orbit.viewmodel.container
import timur.gilfanov.messenger.debug.DataScenario
import timur.gilfanov.messenger.debug.DebugDataRepository
import timur.gilfanov.messenger.debug.DebugNotificationService
import timur.gilfanov.messenger.debug.DebugSettings
import timur.gilfanov.messenger.debug.GetSettingsError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.util.Logger

/**
 * ViewModel for debug settings screen.
 * Manages debug configuration state and provides actions for the UI.
 */
@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val debugDataRepository: DebugDataRepository,
    private val debugNotificationService: DebugNotificationService,
    private val logger: Logger,
) : ViewModel(),
    ContainerHost<DebugSettingsUiState, Nothing> {

    companion object {
        private const val TAG = "DebugSettingsViewModel"
    }

    override val container = container<DebugSettingsUiState, Nothing>(
        DebugSettingsUiState(),
    ) {
        coroutineScope {
            launch { observeDebugSettings() }
        }
    }

    @OptIn(OrbitExperimental::class)
    private suspend fun observeDebugSettings() = subIntent {
        repeatOnSubscription {
            debugDataRepository.settings
                .filterIsInstance<Success<DebugSettings, GetSettingsError.ReadError>>()
                .collect { settings ->
                    withContext(Dispatchers.Main) {
                        reduce {
                            state.copy(settings = settings.data)
                        }
                    }
                }
        }
    }

    /**
     * Regenerate data using current scenario
     */
    @OptIn(OrbitExperimental::class)
    fun regenerateData() = intent {
        reduce { state.copy(isLoading = true) }
        logger.d(TAG, "Regenerating data...")

        val result = debugDataRepository.regenerateData()
        when (result) {
            is Success -> {
                // Update notification with current scenario
                val currentSettings = state.settings
                debugNotificationService.updateNotification(currentSettings.scenario)
                logger.d(TAG, "Data regeneration completed")
            }
            is Failure -> {
                logger.w(TAG, "Failed to regenerate data: ${result.error}")
            }
        }
        reduce { state.copy(isLoading = false) }
    }

    /**
     * Clear all data
     */
    @OptIn(OrbitExperimental::class)
    fun clearAllData() = intent {
        reduce { state.copy(isLoading = true) }
        logger.d(TAG, "Clearing all data...")

        val result = debugDataRepository.clearData()
        when (result) {
            is Success -> {
                logger.d(TAG, "Data cleared successfully")
            }
            is Failure -> {
                logger.w(TAG, "Failed to clear data: ${result.error}")
            }
        }
        reduce { state.copy(isLoading = false) }
    }

    /**
     * Switch to a different data scenario
     */
    @OptIn(OrbitExperimental::class)
    fun switchScenario(scenario: DataScenario) = intent {
        reduce { state.copy(isLoading = true) }
        logger.d(TAG, "Switching to scenario: ${scenario.name}")

        val result = debugDataRepository.initializeWithScenario(scenario)
        when (result) {
            is Success -> {
                // Update notification with new scenario
                debugNotificationService.updateNotification(scenario)
                logger.d(TAG, "Scenario switch completed")
            }
            is Failure -> {
                logger.e(TAG, "Failed to switch scenario: ${result.error}")
            }
        }
        reduce { state.copy(isLoading = false) }
    }

    /**
     * Toggle auto-activity simulation
     */
    @OptIn(OrbitExperimental::class)
    fun toggleAutoActivity(enabled: Boolean) = intent {
        logger.d(TAG, "Toggling auto-activity: $enabled")
        val result = debugDataRepository.updateSettings { settings ->
            settings.copy(autoActivity = enabled)
        }
        if (result is Failure) {
            logger.w(TAG, "Failed to update auto-activity setting: ${result.error}")
        }
    }

    /**
     * Toggle debug notification visibility
     */
    @OptIn(OrbitExperimental::class)
    fun toggleNotification(show: Boolean) = intent {
        logger.d(TAG, "Toggling notification visibility: $show")
        val result = debugDataRepository.updateSettings { settings ->
            settings.copy(showNotification = show)
        }
        if (result is Failure) {
            logger.w(TAG, "Failed to update notification setting: ${result.error}")
            return@intent
        }

        if (show) {
            val currentSettings = state.settings
            debugNotificationService.showPersistentDebugNotification(
                currentSettings.scenario,
            )
        } else {
            debugNotificationService.hideDebugNotification()
        }
    }
}
