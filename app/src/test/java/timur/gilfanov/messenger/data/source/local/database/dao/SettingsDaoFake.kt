package timur.gilfanov.messenger.data.source.local.database.dao

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity

class SettingsDaoFake(private val realDao: SettingsDao) : SettingsDao {

    var simulateDatabaseError: SQLiteException? = null

    var failNextNCalls: Int = 0
    private val queuedErrors = ArrayDeque<SQLiteException>()

    var callCount: Int = 0
        private set

    fun enqueueErrors(vararg errors: SQLiteException) {
        queuedErrors.addAll(errors.toList())
    }

    @Suppress("ThrowsCount") // ok for test fake
    private fun checkDatabaseHealth() {
        callCount++
        if (queuedErrors.isNotEmpty()) {
            throw queuedErrors.removeFirst()
        }
        if (failNextNCalls > 0 && callCount <= failNextNCalls) {
            throw SQLiteDatabaseLockedException("database is locked")
        }
        simulateDatabaseError?.let { throw it }
    }

    override fun observeAllByUser(userKey: String): Flow<List<SettingEntity>> = flow {
        checkDatabaseHealth()
        emitAll(realDao.observeAllByUser(userKey))
    }

    override suspend fun get(userKey: String, key: String): SettingEntity? {
        checkDatabaseHealth()
        return realDao.get(userKey, key)
    }

    override suspend fun getAll(userKey: String): List<SettingEntity> {
        checkDatabaseHealth()
        return realDao.getAll(userKey)
    }

    override suspend fun getUnsynced(userKey: String): List<SettingEntity> {
        checkDatabaseHealth()
        return realDao.getUnsynced(userKey)
    }

    override suspend fun upsert(setting: SettingEntity) {
        checkDatabaseHealth()
        realDao.upsert(setting)
    }

    override suspend fun upsert(settings: List<SettingEntity>) {
        checkDatabaseHealth()
        realDao.upsert(settings)
    }

    override suspend fun deleteAllByUser(userKey: String) {
        checkDatabaseHealth()
        realDao.deleteAllByUser(userKey)
    }
}
