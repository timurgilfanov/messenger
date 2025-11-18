package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.dao.SettingsDaoWithErrorInjection
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class LocalSettingsDataSourceImplExceptionTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private lateinit var wrappedDao: SettingsDaoWithErrorInjection
    private lateinit var dataSource: LocalSettingsDataSource

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @Before
    fun setup() {
        val realDao = databaseRule.database.settingsDao()
        wrappedDao = SettingsDaoWithErrorInjection(realDao)
        dataSource = LocalSettingsDataSourceImpl(
            database = databaseRule.database,
            settingsDao = wrappedDao,
            logger = NoOpLogger(),
        )
    }

    // getSetting() exception tests
    @Test
    fun `getSetting returns DatabaseCorrupted on SQLiteDatabaseCorruptException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteDatabaseCorruptException("database corrupted")

        // When
        val result = dataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        // Then
        assertIs<ResultWithError.Failure<SettingEntity, GetSettingError>>(result)
        assertEquals(GetSettingError.DatabaseCorrupted, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `getSetting returns AccessDenied on SQLiteAccessPermException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteAccessPermException("access denied")

        // When
        val result = dataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        // Then
        assertIs<ResultWithError.Failure<SettingEntity, GetSettingError>>(result)
        assertEquals(GetSettingError.AccessDenied, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `getSetting returns ReadOnlyDatabase on SQLiteReadOnlyDatabaseException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteReadOnlyDatabaseException("read only")

        // When
        val result = dataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        // Then
        assertIs<ResultWithError.Failure<SettingEntity, GetSettingError>>(result)
        assertEquals(GetSettingError.ReadOnlyDatabase, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `getSetting returns ConcurrentModificationError after 3 lock retries`() = runTest {
        // Given - fail all 4 attempts (initial + 3 retries)
        wrappedDao.failNextNCalls = 4

        // When
        val result = dataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        // Then
        assertIs<ResultWithError.Failure<SettingEntity, GetSettingError>>(result)
        assertEquals(GetSettingError.ConcurrentModificationError, result.error)
        assertEquals(4, wrappedDao.callCount) // Initial + 3 retries
    }

    @Test
    fun `getSetting succeeds after 2 lock retries`() = runTest {
        // Given - fail first 2 attempts, succeed on 3rd
        wrappedDao.failNextNCalls = 2
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")
        databaseRule.database.settingsDao().upsert(entity)

        // When
        val result = dataSource.getSetting(testUserId, SettingKey.UI_LANGUAGE.key)

        // Then
        assertIs<ResultWithError.Success<SettingEntity, GetSettingError>>(result)
        assertEquals("en", result.data.value)
        assertEquals(3, wrappedDao.callCount) // Succeeded on 3rd attempt
    }

    // upsert(single) exception tests
    @Test
    fun `upsert single returns StorageFull on SQLiteFullException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteFullException("storage full")
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")

        // When
        val result = dataSource.upsert(entity)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.StorageFull, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `upsert single returns DatabaseCorrupted on SQLiteDatabaseCorruptException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteDatabaseCorruptException("corrupted")
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")

        // When
        val result = dataSource.upsert(entity)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.DatabaseCorrupted, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `upsert single returns AccessDenied on SQLiteAccessPermException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteAccessPermException("access denied")
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")

        // When
        val result = dataSource.upsert(entity)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.AccessDenied, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `upsert single returns ReadOnlyDatabase on SQLiteReadOnlyDatabaseException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteReadOnlyDatabaseException("read only")
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")

        // When
        val result = dataSource.upsert(entity)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.ReadOnlyDatabase, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `upsert single returns ConcurrentModificationError after 3 retries`() = runTest {
        // Given
        wrappedDao.failNextNCalls = 4 // Fail initial + 3 retries
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")

        // When
        val result = dataSource.upsert(entity)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.ConcurrentModificationError, result.error)
        assertEquals(4, wrappedDao.callCount)
    }

    @Test
    fun `upsert single succeeds after 2 retries`() = runTest {
        // Given
        wrappedDao.failNextNCalls = 2
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")

        // When
        val result = dataSource.upsert(entity)

        // Then
        assertIs<ResultWithError.Success<Unit, UpsertSettingError>>(result)
        assertEquals(3, wrappedDao.callCount)
    }

    // upsert(batch) exception tests - NO RETRY
    @Test
    fun `upsert batch returns StorageFull on SQLiteFullException without retry`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteFullException("storage full")
        val entities = listOf(createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en"))

        // When
        val result = dataSource.upsert(entities)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.StorageFull, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry for batch
    }

    @Test
    fun `upsert batch returns DatabaseCorrupted on SQLiteDatabaseCorruptException without retry`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteDatabaseCorruptException("corrupted")
        val entities = listOf(createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en"))

        // When
        val result = dataSource.upsert(entities)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.DatabaseCorrupted, result.error)
        assertEquals(1, wrappedDao.callCount)
    }

    @Test
    fun `upsert batch returns ConcurrentModificationError on lock without retry`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteDatabaseLockedException("locked")
        val entities = listOf(createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en"))

        // When
        val result = dataSource.upsert(entities)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.ConcurrentModificationError, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry for batch
    }

    @Test
    fun `upsert batch returns DiskIOError on SQLiteDiskIOException without retry`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteDiskIOException("disk IO error")
        val entities = listOf(createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en"))

        // When
        val result = dataSource.upsert(entities)

        // Then
        assertIs<ResultWithError.Failure<Unit, UpsertSettingError>>(result)
        assertEquals(UpsertSettingError.DiskIOError, result.error)
        assertEquals(1, wrappedDao.callCount)
    }

    // transform() exception tests - NO RETRY
    @Test
    fun `transform returns StorageFull on SQLiteFullException`() = runTest {
        // Given
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")
        databaseRule.database.settingsDao().upsert(entity)

        wrappedDao.simulateDatabaseError = SQLiteFullException("storage full")

        // When
        val result = dataSource.transform(testUserId) { it }

        // Then
        assertIs<ResultWithError.Failure<Unit, TransformSettingError>>(result)
        assertEquals(TransformSettingError.StorageFull, result.error)
    }

    @Test
    fun `transform returns DatabaseCorrupted on SQLiteDatabaseCorruptException`() = runTest {
        // Given
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")
        databaseRule.database.settingsDao().upsert(entity)

        wrappedDao.simulateDatabaseError = SQLiteDatabaseCorruptException("corrupted")

        // When
        val result = dataSource.transform(testUserId) { it }

        // Then
        assertIs<ResultWithError.Failure<Unit, TransformSettingError>>(result)
        assertEquals(TransformSettingError.DatabaseCorrupted, result.error)
    }

    @Test
    fun `transform returns AccessDenied on SQLiteAccessPermException`() = runTest {
        // Given
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")
        databaseRule.database.settingsDao().upsert(entity)

        wrappedDao.simulateDatabaseError = SQLiteAccessPermException("access denied")

        // When
        val result = dataSource.transform(testUserId) { it }

        // Then
        assertIs<ResultWithError.Failure<Unit, TransformSettingError>>(result)
        assertEquals(TransformSettingError.AccessDenied, result.error)
    }

    @Test
    fun `transform returns ReadOnlyDatabase on SQLiteReadOnlyDatabaseException`() = runTest {
        // Given
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")
        databaseRule.database.settingsDao().upsert(entity)

        wrappedDao.simulateDatabaseError = SQLiteReadOnlyDatabaseException("read only")

        // When
        val result = dataSource.transform(testUserId) { it }

        // Then
        assertIs<ResultWithError.Failure<Unit, TransformSettingError>>(result)
        assertEquals(TransformSettingError.ReadOnlyDatabase, result.error)
    }

    @Test
    fun `transform returns ConcurrentModificationError on SQLiteDatabaseLockedException`() = runTest {
        // Given
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")
        databaseRule.database.settingsDao().upsert(entity)

        wrappedDao.simulateDatabaseError = SQLiteDatabaseLockedException("locked")

        // When
        val result = dataSource.transform(testUserId) { it }

        // Then
        assertIs<ResultWithError.Failure<Unit, TransformSettingError>>(result)
        assertEquals(TransformSettingError.ConcurrentModificationError, result.error)
    }

    @Test
    fun `transform returns DiskIOError on SQLiteDiskIOException`() = runTest {
        // Given
        val entity = createSettingEntity(testUserId, SettingKey.UI_LANGUAGE, "en")
        databaseRule.database.settingsDao().upsert(entity)

        wrappedDao.simulateDatabaseError = SQLiteDiskIOException("disk IO error")

        // When
        val result = dataSource.transform(testUserId) { it }

        // Then
        assertIs<ResultWithError.Failure<Unit, TransformSettingError>>(result)
        assertEquals(TransformSettingError.DiskIOError, result.error)
    }

    // getUnsyncedSettings() exception tests
    @Test
    fun `getUnsyncedSettings returns DatabaseCorrupted on SQLiteDatabaseCorruptException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteDatabaseCorruptException("corrupted")

        // When
        val result = dataSource.getUnsyncedSettings()

        // Then
        assertIs<ResultWithError.Failure<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertEquals(GetUnsyncedSettingsError.DatabaseCorrupted, result.error)
        assertEquals(1, wrappedDao.callCount) // No retry
    }

    @Test
    fun `getUnsyncedSettings returns AccessDenied on SQLiteAccessPermException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteAccessPermException("access denied")

        // When
        val result = dataSource.getUnsyncedSettings()

        // Then
        assertIs<ResultWithError.Failure<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertEquals(GetUnsyncedSettingsError.AccessDenied, result.error)
        assertEquals(1, wrappedDao.callCount)
    }

    @Test
    fun `getUnsyncedSettings returns ReadOnlyDatabase on SQLiteReadOnlyDatabaseException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteReadOnlyDatabaseException("read only")

        // When
        val result = dataSource.getUnsyncedSettings()

        // Then
        assertIs<ResultWithError.Failure<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertEquals(GetUnsyncedSettingsError.ReadOnlyDatabase, result.error)
        assertEquals(1, wrappedDao.callCount)
    }

    @Test
    fun `getUnsyncedSettings returns ConcurrentModificationError after 3 retries`() = runTest {
        // Given
        wrappedDao.failNextNCalls = 4

        // When
        val result = dataSource.getUnsyncedSettings()

        // Then
        assertIs<ResultWithError.Failure<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertEquals(GetUnsyncedSettingsError.ConcurrentModificationError, result.error)
        assertEquals(4, wrappedDao.callCount)
    }

    @Test
    fun `getUnsyncedSettings succeeds after 2 retries`() = runTest {
        // Given
        wrappedDao.failNextNCalls = 2
        val entity = createSettingEntity(
            testUserId,
            SettingKey.UI_LANGUAGE,
            "en",
            localVersion = 2,
            syncedVersion = 1,
        )
        databaseRule.database.settingsDao().upsert(entity)

        // When
        val result = dataSource.getUnsyncedSettings()

        // Then
        assertIs<ResultWithError.Success<List<SettingEntity>, GetUnsyncedSettingsError>>(result)
        assertTrue(result.data.isNotEmpty())
        assertEquals(3, wrappedDao.callCount)
    }

    // observe() exception tests using Turbine
    @Test
    fun `observe emits InsufficientStorage on SQLiteFullException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteFullException("storage full")

        // When/Then
        dataSource.observe(testUserId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<LocalSettings, GetSettingsLocalDataSourceError>>(result)
            assertEquals(
                GetSettingsLocalDataSourceError.Recoverable.InsufficientStorage,
                result.error,
            )
            awaitComplete()
        }
    }

    @Test
    fun `observe emits DataCorruption on SQLiteDatabaseCorruptException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteDatabaseCorruptException("corrupted")

        // When/Then
        dataSource.observe(testUserId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<LocalSettings, GetSettingsLocalDataSourceError>>(result)
            assertEquals(
                GetSettingsLocalDataSourceError.Recoverable.DataCorruption,
                result.error,
            )
            awaitComplete()
        }
    }

    @Test
    fun `observe emits AccessDenied on SQLiteAccessPermException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteAccessPermException("access denied")

        // When/Then
        dataSource.observe(testUserId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<LocalSettings, GetSettingsLocalDataSourceError>>(result)
            assertEquals(
                GetSettingsLocalDataSourceError.Recoverable.AccessDenied,
                result.error,
            )
            awaitComplete()
        }
    }

    @Test
    fun `observe emits ReadOnly on SQLiteReadOnlyDatabaseException`() = runTest {
        // Given
        wrappedDao.simulateDatabaseError = SQLiteReadOnlyDatabaseException("read only")

        // When/Then
        dataSource.observe(testUserId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<LocalSettings, GetSettingsLocalDataSourceError>>(result)
            assertEquals(
                GetSettingsLocalDataSourceError.Recoverable.ReadOnly,
                result.error,
            )
            awaitComplete()
        }
    }

    @Test
    fun `observe emits TemporarilyUnavailable after 3 lock retries`() = runTest {
        // Given
        wrappedDao.failNextNCalls = 4 // Fail initial + 3 retries

        // When/Then
        dataSource.observe(testUserId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<LocalSettings, GetSettingsLocalDataSourceError>>(result)
            assertEquals(
                GetSettingsLocalDataSourceError.Recoverable.TemporarilyUnavailable,
                result.error,
            )
            awaitComplete()
        }
    }

    @Suppress("LongParameterList")
    private fun createSettingEntity(
        userId: UserId,
        key: SettingKey,
        value: String,
        localVersion: Int = 1,
        syncedVersion: Int = 0,
        serverVersion: Int = 0,
        modifiedAt: Long = 0L,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ): SettingEntity = SettingEntity(
        userId = userId.id.toString(),
        key = key.key,
        value = value,
        localVersion = localVersion,
        syncedVersion = syncedVersion,
        serverVersion = serverVersion,
        modifiedAt = modifiedAt,
        syncStatus = syncStatus,
    )
}
