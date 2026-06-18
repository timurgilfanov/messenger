package timur.gilfanov.messenger.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryFake
import timur.gilfanov.messenger.domain.usecase.settings.repository.DeleteUserDataRepositoryError

@Category(Unit::class)
class SettingsUserScopedDataCleanerTest {
    private val userKey = UserScopeKey("user-key")

    @Test
    fun `deleteUserData returns success when settings delete succeeds`() = runTest {
        val cleaner = SettingsUserScopedDataCleaner(
            SettingsRepositoryFake(initialSettings = Settings(UiLanguage.English)),
        )

        val result = cleaner.deleteUserData(userKey)

        assertIs<ResultWithError.Success<kotlin.Unit, *>>(result)
    }

    @Test
    fun `deleteUserData maps LocalOperationFailed to its LocalStorageError`() = runTest {
        val cleaner = SettingsUserScopedDataCleaner(
            SettingsRepositoryFake(
                initialSettings = Settings(UiLanguage.English),
                deleteUserDataResult = ResultWithError.Failure(
                    DeleteUserDataRepositoryError.LocalOperationFailed(
                        LocalStorageError.AccessDenied,
                    ),
                ),
            ),
        )

        val result = cleaner.deleteUserData(userKey)

        val failure = assertIs<ResultWithError.Failure<*, LocalStorageError>>(result)
        assertEquals(LocalStorageError.AccessDenied, failure.error)
    }
}
