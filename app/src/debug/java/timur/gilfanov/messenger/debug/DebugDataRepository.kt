package timur.gilfanov.messenger.debug

import kotlinx.coroutines.flow.Flow

interface DebugDataRepository {
    suspend fun getScenario(): DataScenario?

    suspend fun initializeWithScenario(scenario: DataScenario)

    suspend fun regenerateData()

    suspend fun clearData()

    val settings: Flow<DebugSettings>

    suspend fun updateSettings(transform: (DebugSettings) -> DebugSettings)

    suspend fun setAutoActivity(enabled: Boolean)

    suspend fun setNotification(show: Boolean)
}
