package timur.gilfanov.messenger.profile.data.repository

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError
import timur.gilfanov.messenger.profile.data.source.local.LocalProfileDataSource
import timur.gilfanov.messenger.profile.data.source.local.ProfileLocalDataSourceError
import timur.gilfanov.messenger.profile.data.source.remote.RemoteProfileDataSource
import timur.gilfanov.messenger.profile.data.source.remote.RemoteProfileDataSourceError

@Category(Unit::class)
class ProfileRepositoryImplTest {
    private val userKey = UserScopeKey("user-key")

    @Test
    fun `observeCurrentProfile emits cached profile before remote profile`() = runTest {
        val localDataSource = FakeLocalProfileDataSource(
            initialProfile = Profile("Cached", null),
        )
        val repository = ProfileRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = FakeRemoteProfileDataSource(Profile("Remote", "avatar")),
        )

        repository.observeCurrentProfile(userKey).test {
            assertEquals(Profile("Cached", null), awaitSuccess())
            assertEquals(Profile("Remote", "avatar"), awaitSuccess())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeCurrentProfile upserts remote profile into local cache`() = runTest {
        val localDataSource = FakeLocalProfileDataSource()
        val repository = ProfileRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = FakeRemoteProfileDataSource(Profile("Remote", null)),
        )

        repository.observeCurrentProfile(userKey).test {
            assertEquals(Profile("Remote", null), awaitSuccess())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Profile("Remote", null), localDataSource.cachedProfile)
    }

    @Test
    fun `observeCurrentProfile emits remote failure when cache is missing`() = runTest {
        val repository = ProfileRepositoryImpl(
            localDataSource = FakeLocalProfileDataSource(),
            remoteDataSource = FakeRemoteProfileDataSource(
                error = RemoteProfileDataSourceError.RemoteDataSource(
                    RemoteDataSourceError.RateLimitExceeded,
                ),
            ),
        )

        repository.observeCurrentProfile(userKey).test {
            assertIs<ObserveProfileRepositoryError.RemoteOperationFailed>(awaitFailure())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeCurrentProfile suppresses remote failure when cache exists`() = runTest {
        val repository = ProfileRepositoryImpl(
            localDataSource = FakeLocalProfileDataSource(Profile("Cached", null)),
            remoteDataSource = FakeRemoteProfileDataSource(
                error = RemoteProfileDataSourceError.RemoteDataSource(
                    RemoteDataSourceError.RateLimitExceeded,
                ),
            ),
        )

        repository.observeCurrentProfile(userKey).test {
            assertEquals(Profile("Cached", null), awaitSuccess())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<
        ResultWithError<Profile, ObserveProfileRepositoryError>,
        >.awaitSuccess(): Profile {
        val result = assertIs<ResultWithError.Success<Profile, *>>(awaitItem())
        return result.data
    }

    private suspend fun ReceiveTurbine<
        ResultWithError<Profile, ObserveProfileRepositoryError>,
        >.awaitFailure(): ObserveProfileRepositoryError {
        val result = assertIs<ResultWithError.Failure<*, ObserveProfileRepositoryError>>(
            awaitItem(),
        )
        return result.error
    }

    private class FakeLocalProfileDataSource(initialProfile: Profile? = null) :
        LocalProfileDataSource {
        private val profile = MutableStateFlow(initialProfile)
        val cachedProfile: Profile?
            get() = profile.value

        override fun observeProfile(
            userKey: UserScopeKey,
        ): Flow<ResultWithError<Profile, ProfileLocalDataSourceError>> = profile.map {
            if (it == null) {
                ResultWithError.Failure<Profile, ProfileLocalDataSourceError>(
                    ProfileLocalDataSourceError.UserDataNotFound,
                )
            } else {
                ResultWithError.Success<Profile, ProfileLocalDataSourceError>(it)
            }
        }

        override suspend fun upsertProfile(
            userKey: UserScopeKey,
            profile: Profile,
        ): ResultWithError<kotlin.Unit, ProfileLocalDataSourceError> {
            this.profile.value = profile
            return ResultWithError.Success(kotlin.Unit)
        }

        override suspend fun deleteProfile(
            userKey: UserScopeKey,
        ): ResultWithError<kotlin.Unit, ProfileLocalDataSourceError> {
            profile.value = null
            return ResultWithError.Success(kotlin.Unit)
        }
    }

    private class FakeRemoteProfileDataSource(
        private val profile: Profile? = null,
        private val error: RemoteProfileDataSourceError? = null,
    ) : RemoteProfileDataSource {
        constructor(profile: Profile) : this(profile = profile, error = null)

        override suspend fun getCurrentProfile(): ResultWithError<
            Profile,
            RemoteProfileDataSourceError,
            > =
            error?.let { ResultWithError.Failure<Profile, RemoteProfileDataSourceError>(it) }
                ?: ResultWithError.Success(requireNotNull(profile))
    }
}
