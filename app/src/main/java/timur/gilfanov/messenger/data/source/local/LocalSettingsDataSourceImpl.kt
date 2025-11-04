package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import timur.gilfanov.messenger.data.source.local.database.dao.SettingsDao
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UserId

class LocalSettingsDataSourceImpl @Inject constructor(private val settingsDao: SettingsDao) :
    LocalSettingsDataSource {

    override fun observeSettingEntities(userId: UserId): Flow<List<SettingEntity>> =
        settingsDao.observeAllByUser(userId.id.toString())
            .retryWhen { cause, attempt ->
                when {
                    isTransientError(cause) && attempt < MAX_RETRIES -> {
                        delay(calculateBackoff(attempt))
                        true
                    }

                    else -> false
                }
            }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getSetting(
        userId: UserId,
        key: String,
    ): ResultWithError<SettingEntity, GetSettingError> = try {
        val entity = settingsDao.get(userId.id.toString(), key)
        if (entity != null) {
            ResultWithError.Success(entity)
        } else {
            ResultWithError.Failure(GetSettingError.SettingNotFound)
        }
    } catch (e: Exception) {
        ResultWithError.Failure(mapToGetSettingError(e))
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun updateSetting(
        entity: SettingEntity,
    ): ResultWithError<Unit, UpdateSettingError> = try {
        val existing = settingsDao.get(entity.userId, entity.key)
        if (existing == null) {
            settingsDao.insert(entity)
        } else {
            settingsDao.update(entity)
        }
        ResultWithError.Success(Unit)
    } catch (e: Exception) {
        ResultWithError.Failure(mapToUpdateSettingError(e))
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getUnsyncedSettings(): ResultWithError<
        List<SettingEntity>,
        GetUnsyncedSettingsError,
        > =
        try {
            val settings = settingsDao.getUnsynced()
            ResultWithError.Success(settings)
        } catch (e: Exception) {
            ResultWithError.Failure(mapToGetUnsyncedSettingsError(e))
        }

    private fun isTransientError(cause: Throwable): Boolean =
        cause is SQLiteDatabaseLockedException || cause is SQLiteDiskIOException

    private fun calculateBackoff(attempt: Long): Long =
        (INITIAL_BACKOFF_MS * (1 shl attempt.toInt())).coerceAtMost(
            MAX_BACKOFF_MS,
        ).milliseconds.inWholeMilliseconds

    private fun mapToGetSettingError(exception: Exception): GetSettingError = when (exception) {
        is SQLiteDiskIOException, is SQLiteDatabaseLockedException ->
            GetSettingError.StorageUnavailable

        else -> GetSettingError.UnknownError(exception)
    }

    private fun mapToUpdateSettingError(exception: Exception): UpdateSettingError =
        when (exception) {
            is SQLiteDiskIOException, is SQLiteDatabaseLockedException ->
                UpdateSettingError.StorageUnavailable

            is SQLiteFullException -> UpdateSettingError.StorageFull
            else -> UpdateSettingError.UnknownError(exception)
        }

    private fun mapToGetUnsyncedSettingsError(exception: Exception): GetUnsyncedSettingsError =
        when (exception) {
            is SQLiteDiskIOException, is SQLiteDatabaseLockedException ->
                GetUnsyncedSettingsError.StorageUnavailable

            else -> GetUnsyncedSettingsError.UnknownError(exception)
        }

    companion object {
        private const val MAX_RETRIES = 3L
        private const val INITIAL_BACKOFF_MS = 100L
        private const val MAX_BACKOFF_MS = 2000L

        fun createDefaultEntity(userId: UserId, key: String, defaultValue: String): SettingEntity =
            SettingEntity(
                userId = userId.id.toString(),
                key = key,
                value = defaultValue,
                localVersion = 0,
                syncedVersion = 0,
                serverVersion = 0,
                modifiedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
            )
    }
}
