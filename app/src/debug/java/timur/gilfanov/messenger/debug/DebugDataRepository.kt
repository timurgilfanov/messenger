package timur.gilfanov.messenger.debug

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError

interface DebugDataRepository {
    suspend fun initializeWithScenario(
        scenario: DataScenario,
    ): ResultWithError<Unit, RegenerateDataError>

    suspend fun regenerateData(): ResultWithError<Unit, RegenerateDataError>

    suspend fun clearData(): ResultWithError<Unit, ClearDataError>

    suspend fun clearSyncTimestamp(): ResultWithError<Unit, ClearSyncTimestampError>

    val settings: Flow<ResultWithError<DebugSettings, GetSettingsError.ReadError>>

    suspend fun getSettings(): ResultWithError<DebugSettings, GetSettingsError>

    suspend fun updateSettings(
        transform: (DebugSettings) -> DebugSettings,
    ): ResultWithError<Unit, UpdateSettingsError>
}
