package timur.gilfanov.messenger.domain.usecase.profile

import kotlin.test.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError

@Category(Unit::class)
class ProfileObserveErrorTest {

    @Test
    fun `ObserveProfileError variants expose their cause`() {
        assertEquals(ObserveProfileError.Unauthorized, ObserveProfileError.Unauthorized)
        assertEquals(
            LocalStorageError.AccessDenied,
            ObserveProfileError.LocalOperationFailed(LocalStorageError.AccessDenied).error,
        )
        assertEquals(
            RemoteError.Unauthenticated,
            ObserveProfileError.RemoteOperationFailed(RemoteError.Unauthenticated).error,
        )
    }

    @Test
    fun `ObserveProfileRepositoryError variants expose their cause`() {
        assertEquals(
            LocalStorageError.AccessDenied,
            ObserveProfileRepositoryError.LocalOperationFailed(
                LocalStorageError.AccessDenied,
            ).error,
        )
        assertEquals(
            RemoteError.Unauthenticated,
            ObserveProfileRepositoryError.RemoteOperationFailed(RemoteError.Unauthenticated).error,
        )
    }
}
