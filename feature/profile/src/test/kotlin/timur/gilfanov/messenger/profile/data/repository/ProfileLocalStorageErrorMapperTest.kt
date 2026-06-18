package timur.gilfanov.messenger.profile.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.profile.data.source.local.LocalDataSourceErrorV2
import timur.gilfanov.messenger.profile.data.source.local.ProfileLocalDataSourceError

@Category(Unit::class)
class ProfileLocalStorageErrorMapperTest {

    @Test
    fun `UserDataNotFound maps to UnknownError`() {
        val result = ProfileLocalDataSourceError.UserDataNotFound.toLocalStorageError()
        assertIs<LocalStorageError.UnknownError>(result)
    }

    @Test
    fun `read error maps to TemporarilyUnavailable`() {
        assertEquals(
            LocalStorageError.TemporarilyUnavailable,
            local(LocalDataSourceErrorV2.ReadError(ErrorReason("io"))),
        )
    }

    @Test
    fun `write error maps to TemporarilyUnavailable`() {
        assertEquals(
            LocalStorageError.TemporarilyUnavailable,
            local(LocalDataSourceErrorV2.WriteError(ErrorReason("io"))),
        )
    }

    @Test
    fun `storage full error maps to StorageFull`() {
        assertEquals(
            LocalStorageError.StorageFull,
            local(LocalDataSourceErrorV2.StorageFullError(ErrorReason("full"))),
        )
    }

    @Test
    fun `read only error maps to ReadOnly`() {
        assertEquals(
            LocalStorageError.ReadOnly,
            local(LocalDataSourceErrorV2.ReadOnlyError(ErrorReason("ro"))),
        )
    }

    @Test
    fun `access denied error maps to AccessDenied`() {
        assertEquals(
            LocalStorageError.AccessDenied,
            local(LocalDataSourceErrorV2.AccessDeniedError(ErrorReason("denied"))),
        )
    }

    @Test
    fun `corrupted error maps to Corrupted`() {
        assertEquals(
            LocalStorageError.Corrupted,
            local(LocalDataSourceErrorV2.CorruptedError(ErrorReason("corrupt"))),
        )
    }

    @Test
    fun `serialization error maps to UnknownError`() {
        assertIs<LocalStorageError.UnknownError>(
            local(LocalDataSourceErrorV2.SerializationError(ErrorReason("ser"))),
        )
    }

    @Test
    fun `deserialization error maps to UnknownError`() {
        assertIs<LocalStorageError.UnknownError>(
            local(LocalDataSourceErrorV2.DeserializationError(ErrorReason("deser"))),
        )
    }

    private fun local(error: LocalDataSourceErrorV2): LocalStorageError =
        ProfileLocalDataSourceError.LocalDataSource(error).toLocalStorageError()
}
