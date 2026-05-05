package timur.gilfanov.messenger.auth.domain.usecase

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LogoutUseCaseImplTest {

    private fun createUseCase(repository: AuthRepositoryFake = AuthRepositoryFake()) =
        LogoutUseCaseImpl(repository, NoOpLogger())

    @Test
    fun `success returns Success`() = runTest {
        val repository = AuthRepositoryFake().apply {
            defaultLogoutResult = ResultWithError.Success(Unit)
        }
        val result = createUseCase(repository)()
        assertEquals(ResultWithError.Success(Unit), result)
    }

    @Test
    fun `local error maps to LocalOperationFailed`() = runTest {
        val localError = LocalStorageError.UnknownError(Exception("disk full"))
        val repository = AuthRepositoryFake().apply {
            defaultLogoutResult =
                ResultWithError.Failure(LogoutRepositoryError.LocalOperationFailed(localError))
        }
        val result = createUseCase(repository)()
        assertIs<ResultWithError.Failure<Unit, LogoutUseCaseError>>(result)
        assertIs<LogoutUseCaseError.LocalOperationFailed>(result.error)
        val resultLocalError = result.error as LogoutUseCaseError.LocalOperationFailed
        assertEquals(localError, resultLocalError.error)
    }

    @Test
    fun `remote error maps to RemoteOperationFailed`() = runTest {
        val remoteError = RemoteError.Failed.NetworkNotAvailable
        val repository = AuthRepositoryFake().apply {
            defaultLogoutResult =
                ResultWithError.Failure(LogoutRepositoryError.RemoteOperationFailed(remoteError))
        }
        val result = createUseCase(repository)()
        assertIs<ResultWithError.Failure<Unit, LogoutUseCaseError>>(result)
        assertIs<LogoutUseCaseError.RemoteOperationFailed>(result.error)
        val resultRemoteError = result.error as LogoutUseCaseError.RemoteOperationFailed
        assertEquals(remoteError, resultRemoteError.error)
    }
}
