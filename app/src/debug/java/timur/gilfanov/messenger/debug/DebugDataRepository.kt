package timur.gilfanov.messenger.debug

import kotlinx.coroutines.flow.Flow

interface DebugDataRepository {

    suspend fun getSavedScenario(): DataScenario?
    suspend fun toggleAutoActivity(enabled: Boolean)
    suspend fun toggleNotification(show: Boolean)
    suspend fun updateSettings(transform: (DebugSettings) -> DebugSettings)
    suspend fun clearAllData()
    suspend fun regenerateData()
    suspend fun initializeWithScenario(scenario: DataScenario)
    val debugSettings: Flow<DebugSettings>
}
