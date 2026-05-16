package timur.gilfanov.messenger.profile.domain.usecase

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.profile.PictureUri
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.ProfileRepository
import timur.gilfanov.messenger.domain.usecase.profile.repository.RemovePictureRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.UpdateNameRepositoryError
import timur.gilfanov.messenger.domain.usecase.profile.repository.UpdatePictureRepositoryError

@Category(Unit::class)
class ObserveProfileUseCaseImplTest {
    private val session = AuthSession(
        tokens = AuthTokens(accessToken = "access", refreshToken = "refresh"),
        provider = AuthProvider.EMAIL,
        sessionId = "test-session-id",
    )

    @Test
    fun `authenticated state observes profile for current user scope`() = runTest {
        val repository = ProfileRepositoryFake(
            result = ResultWithError.Success(Profile("Timur", null)),
        )
        val useCase = ObserveProfileUseCaseImpl(
            authRepository = AuthRepositoryFake(AuthState.Authenticated(session)),
            profileRepository = repository,
        )

        useCase().test {
            val result = assertIs<ResultWithError.Success<Profile, *>>(awaitItem())

            assertEquals(Profile("Timur", null), result.data)
            assertEquals(session.toUserScopeKey(), repository.lastUserKey)
            expectNoEvents()
        }
    }

    @Test
    fun `unauthenticated state emits Unauthorized`() = runTest {
        val useCase = ObserveProfileUseCaseImpl(
            authRepository = AuthRepositoryFake(AuthState.Unauthenticated),
            profileRepository = ProfileRepositoryFake(),
        )

        useCase().test {
            val result = assertIs<ResultWithError.Failure<*, ObserveProfileError>>(awaitItem())

            assertIs<ObserveProfileError.Unauthorized>(result.error)
            expectNoEvents()
        }
    }

    @Test
    fun `repository local failure maps to use case local failure`() = runTest {
        val useCase = ObserveProfileUseCaseImpl(
            authRepository = AuthRepositoryFake(AuthState.Authenticated(session)),
            profileRepository = ProfileRepositoryFake(
                result = ResultWithError.Failure(
                    ObserveProfileRepositoryError.LocalOperationFailed(
                        LocalStorageError.AccessDenied,
                    ),
                ),
            ),
        )

        useCase().test {
            val result = assertIs<ResultWithError.Failure<*, ObserveProfileError>>(awaitItem())

            val error = assertIs<ObserveProfileError.LocalOperationFailed>(result.error)
            assertEquals(LocalStorageError.AccessDenied, error.error)
            expectNoEvents()
        }
    }

    @Test
    fun `repository remote failure maps to use case remote failure`() = runTest {
        val useCase = ObserveProfileUseCaseImpl(
            authRepository = AuthRepositoryFake(AuthState.Authenticated(session)),
            profileRepository = ProfileRepositoryFake(
                result = ResultWithError.Failure(
                    ObserveProfileRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                ),
            ),
        )

        useCase().test {
            val result = assertIs<ResultWithError.Failure<*, ObserveProfileError>>(awaitItem())

            val error = assertIs<ObserveProfileError.RemoteOperationFailed>(result.error)
            assertEquals(RemoteError.Failed.NetworkNotAvailable, error.error)
            expectNoEvents()
        }
    }

    @Test
    fun `repository remote Unauthenticated failure maps to Unauthorized`() = runTest {
        val useCase = ObserveProfileUseCaseImpl(
            authRepository = AuthRepositoryFake(AuthState.Authenticated(session)),
            profileRepository = ProfileRepositoryFake(
                result = ResultWithError.Failure(
                    ObserveProfileRepositoryError.RemoteOperationFailed(
                        RemoteError.Unauthenticated,
                    ),
                ),
            ),
        )

        useCase().test {
            val result = assertIs<ResultWithError.Failure<*, ObserveProfileError>>(awaitItem())

            assertIs<ObserveProfileError.Unauthorized>(result.error)
            expectNoEvents()
        }
    }

    @Test
    fun `profile observation does not restart when only access token changes`() = runTest {
        val authFake = AuthRepositoryFake(AuthState.Authenticated(session))
        val useCase = ObserveProfileUseCaseImpl(
            authRepository = authFake,
            profileRepository = ProfileRepositoryFake(
                result = ResultWithError.Success(Profile("Timur", null)),
            ),
        )

        useCase().test {
            assertIs<ResultWithError.Success<Profile, *>>(awaitItem())

            val refreshedSession = session.copy(
                tokens = session.tokens.copy(accessToken = "new-access"),
            )
            authFake.setState(AuthState.Authenticated(refreshedSession))

            expectNoEvents()
        }
    }

    @Test
    fun `profile observation does not restart when both tokens rotate but session ID unchanged`() =
        runTest {
            val authFake = AuthRepositoryFake(AuthState.Authenticated(session))
            val useCase = ObserveProfileUseCaseImpl(
                authRepository = authFake,
                profileRepository = ProfileRepositoryFake(
                    result = ResultWithError.Success(Profile("Timur", null)),
                ),
            )

            useCase().test {
                assertIs<ResultWithError.Success<Profile, *>>(awaitItem())

                val rotatedSession = session.copy(
                    tokens = AuthTokens(
                        accessToken = "new-access",
                        refreshToken = "new-refresh",
                    ),
                )
                authFake.setState(AuthState.Authenticated(rotatedSession))

                expectNoEvents()
            }
        }

    private class ProfileRepositoryFake(
        private val result: ResultWithError<Profile, ObserveProfileRepositoryError> =
            ResultWithError.Success(Profile("Default", null)),
    ) : ProfileRepository {
        var lastUserKey: UserScopeKey? = null
            private set

        override fun observeCurrentProfile(
            userKey: UserScopeKey,
        ): Flow<ResultWithError<Profile, ObserveProfileRepositoryError>> {
            lastUserKey = userKey
            return flowOf(result)
        }

        override suspend fun updateName(
            userId: UserId,
            name: String,
        ): ResultWithError<kotlin.Unit, UpdateNameRepositoryError> = error("Not used")

        override suspend fun updatePicture(
            userId: UserId,
            pictureUri: PictureUri,
        ): ResultWithError<kotlin.Unit, UpdatePictureRepositoryError> = error("Not used")

        override suspend fun removePicture(
            userId: UserId,
            pictureUri: PictureUri,
        ): ResultWithError<kotlin.Unit, RemovePictureRepositoryError> = error("Not used")
    }
}
