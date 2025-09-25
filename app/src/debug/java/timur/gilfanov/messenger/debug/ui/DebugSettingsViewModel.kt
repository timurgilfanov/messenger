package timur.gilfanov.messenger.debug.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.lang.IllegalArgumentException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.util.Logger

data class DebugSettingsUiState(
    val settings: DebugSettings = DebugSettings(),
    val isLoading: Boolean = false,
)

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
        try {
            reduce { state.copy(isLoading = true) }
            logger.d(TAG, "Regenerating data...")

            debugDataRepository.regenerateData()

            // Update notification with current scenario
            val currentSettings = state.settings
            debugNotificationService.updateNotification(currentSettings.scenario)

            logger.d(TAG, "Data regeneration completed")
        } catch (e: IOException) {
            logger.e(TAG, "Failed to regenerate data - IO error", e)
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation
        } catch (e: SecurityException) {
            logger.e(TAG, "Failed to regenerate data - permission denied", e)
        } catch (e: IllegalArgumentException) {
            logger.e(TAG, "Failed to regenerate data - invalid arguments", e)
        } finally {
            reduce { state.copy(isLoading = false) }
        }
    }

    /**
     * Clear all data
     */
    @OptIn(OrbitExperimental::class)
    fun clearAllData() = intent {
        try {
            reduce { state.copy(isLoading = true) }
            logger.d(TAG, "Clearing all data...")

            debugDataRepository.clearData()

            logger.d(TAG, "Data cleared")
        } catch (e: IOException) {
            logger.e(TAG, "Failed to clear data - IO error", e)
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation
        } catch (e: SecurityException) {
            logger.e(TAG, "Failed to clear data - permission denied", e)
        } catch (e: IllegalArgumentException) {
            logger.e(TAG, "Failed to clear data - invalid arguments", e)
        } finally {
            reduce { state.copy(isLoading = false) }
        }
    }

    /**
     * Switch to a different data scenario
     */
    @OptIn(OrbitExperimental::class)
    fun switchScenario(scenario: DataScenario) = intent {
        try {
            reduce { state.copy(isLoading = true) }
            logger.d(TAG, "Switching to scenario: ${scenario.name}")

            debugDataRepository.initializeWithScenario(scenario)

            // Update notification with new scenario
            debugNotificationService.updateNotification(scenario)

            logger.d(TAG, "Scenario switch completed")
        } catch (e: IOException) {
            logger.e(TAG, "Failed to switch scenario - IO error", e)
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation
        } catch (e: SecurityException) {
            logger.e(TAG, "Failed to switch scenario - permission denied", e)
        } catch (e: IllegalArgumentException) {
            logger.e(TAG, "Failed to switch scenario - invalid arguments", e)
        } finally {
            reduce { state.copy(isLoading = false) }
        }
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
        if (result is ResultWithError.Failure) {
            logger.e(TAG, "Failed to update auto-activity setting: ${result.error}")
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
        if (result is ResultWithError.Failure) {
            logger.e(TAG, "Failed to update notification setting: ${result.error}")
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
