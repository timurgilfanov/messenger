package timur.gilfanov.messenger.profile.data.repository

import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.profile.data.source.local.LocalDataSourceErrorV2
import timur.gilfanov.messenger.profile.data.source.local.LocalProfileDataSource
import timur.gilfanov.messenger.profile.data.source.local.ProfileLocalDataSourceError

@Category(Unit::class)
class ProfileUserScopedDataCleanerTest {
    private val userKey = UserScopeKey("user-key")

    @Test
    fun `deleteUserData returns success when delete succeeds`() = runTest {
        val cleaner = ProfileUserScopedDataCleaner(FakeLocalProfileDataSource())

        val result = cleaner.deleteUserData(userKey)

        assertIs<ResultWithError.Success<kotlin.Unit, *>>(result)
    }

    @Test
    fun `deleteUserData maps write error to TemporarilyUnavailable`() = runTest {
        val deleteError = ProfileLocalDataSourceError.LocalDataSource(
            LocalDataSourceErrorV2.WriteError(ErrorReason("disk full")),
        )
        val cleaner = ProfileUserScopedDataCleaner(FakeLocalProfileDataSource(deleteError))

        val result = cleaner.deleteUserData(userKey)

        val failure = assertIs<ResultWithError.Failure<*, LocalStorageError>>(result)
        assertIs<LocalStorageError.TemporarilyUnavailable>(failure.error)
    }

    private class FakeLocalProfileDataSource(
        private val deleteError: ProfileLocalDataSourceError? = null,
    ) : LocalProfileDataSource {
        override fun observeProfile(
            userKey: UserScopeKey,
        ): Flow<ResultWithError<Profile, ProfileLocalDataSourceError>> = flowOf()

        override suspend fun upsertProfile(
            userKey: UserScopeKey,
            profile: Profile,
        ): ResultWithError<kotlin.Unit, ProfileLocalDataSourceError> = error("Not used")

        override suspend fun deleteProfile(
            userKey: UserScopeKey,
        ): ResultWithError<kotlin.Unit, ProfileLocalDataSourceError> {
            val error = deleteError
            if (error != null) return ResultWithError.Failure(error)
            return ResultWithError.Success(kotlin.Unit)
        }
    }
}
