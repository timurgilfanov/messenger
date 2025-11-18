package timur.gilfanov.messenger.data.source.local.database.dao

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity

class SettingsDaoWithErrorInjection(private val realDao: SettingsDao) : SettingsDao {

    var simulateDatabaseError: SQLiteException? = null

    var failNextNCalls: Int = 0

    var callCount: Int = 0
        private set

    private fun checkDatabaseHealth() {
        callCount++
        if (failNextNCalls > 0 && callCount <= failNextNCalls) {
            throw SQLiteDatabaseLockedException("database is locked")
        }
        simulateDatabaseError?.let { throw it }
    }

    override fun observeAllByUser(userId: String): Flow<List<SettingEntity>> = flow {
        checkDatabaseHealth()
        emitAll(realDao.observeAllByUser(userId))
    }

    override suspend fun get(userId: String, key: String): SettingEntity? {
        checkDatabaseHealth()
        return realDao.get(userId, key)
    }

    override suspend fun getAll(userId: String): List<SettingEntity> {
        checkDatabaseHealth()
        return realDao.getAll(userId)
    }

    override suspend fun getUnsynced(): List<SettingEntity> {
        checkDatabaseHealth()
        return realDao.getUnsynced()
    }

    override suspend fun upsert(setting: SettingEntity) {
        checkDatabaseHealth()
        realDao.upsert(setting)
    }

    override suspend fun upsert(settings: List<SettingEntity>) {
        checkDatabaseHealth()
        realDao.upsert(settings)
    }
}
