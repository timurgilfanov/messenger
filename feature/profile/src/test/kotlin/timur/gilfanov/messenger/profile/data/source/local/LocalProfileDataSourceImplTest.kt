package timur.gilfanov.messenger.profile.data.source.local

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.testutil.NoOpLogger

@Category(Unit::class)
class LocalProfileDataSourceImplTest {
    private val userKey = UserScopeKey("user-key")

    @Test
    fun `observeProfile emits UserDataNotFound when no row exists`() = runTest {
        val dataSource = LocalProfileDataSourceImpl(FakeProfileDao(), NoOpLogger())

        dataSource.observeProfile(userKey).test {
            val result = assertIs<ResultWithError.Failure<*, ProfileLocalDataSourceError>>(
                awaitItem(),
            )

            assertIs<ProfileLocalDataSourceError.UserDataNotFound>(result.error)
        }
    }

    @Test
    fun `upsertProfile stores row observed by observeProfile`() = runTest {
        val dataSource = LocalProfileDataSourceImpl(FakeProfileDao(), NoOpLogger())

        dataSource.upsertProfile(userKey, Profile("Timur", "https://example.com/avatar.jpg"))

        dataSource.observeProfile(userKey).test {
            val result = assertIs<ResultWithError.Success<Profile, *>>(awaitItem())

            assertEquals(Profile("Timur", "https://example.com/avatar.jpg"), result.data)
        }
    }

    @Test
    fun `observeProfile maps SQLiteException to read error`() = runTest {
        val dao = FakeProfileDao(observeException = SQLiteException("read failed"))
        val dataSource = LocalProfileDataSourceImpl(dao, NoOpLogger())

        dataSource.observeProfile(userKey).test {
            val result = assertIs<ResultWithError.Failure<*, ProfileLocalDataSourceError>>(
                awaitItem(),
            )
            val error = assertIs<ProfileLocalDataSourceError.LocalDataSource>(result.error)

            assertIs<LocalDataSourceErrorV2.ReadError>(error.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsertProfile maps SQLiteException to write error`() = runTest {
        val dao = FakeProfileDao(writeException = SQLiteFullException("full"))
        val dataSource = LocalProfileDataSourceImpl(dao, NoOpLogger())

        val result = dataSource.upsertProfile(userKey, Profile("Timur", null))

        val failure = assertIs<ResultWithError.Failure<*, ProfileLocalDataSourceError>>(result)
        val error = assertIs<ProfileLocalDataSourceError.LocalDataSource>(failure.error)
        assertIs<LocalDataSourceErrorV2.WriteError>(error.error)
    }

    private class FakeProfileDao(
        private val observeException: SQLiteException? = null,
        private val writeException: SQLiteException? = null,
    ) : ProfileDao {
        private val rows = MutableStateFlow<Map<String, ProfileEntity>>(emptyMap())

        override fun observeProfile(userKey: String): Flow<ProfileEntity?> {
            val exception = observeException
            return if (exception != null) {
                flow { throw exception }
            } else {
                rows.map { it[userKey] }
            }
        }

        override suspend fun upsert(profile: ProfileEntity) {
            writeException?.let { throw it }
            rows.value = rows.value + (profile.userKey to profile)
        }

        override suspend fun deleteByUserKey(userKey: String) {
            writeException?.let { throw it }
            rows.value = rows.value - userKey
        }
    }
}
