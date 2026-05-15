package timur.gilfanov.messenger.domain

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

/**
 * Deletes local data scoped to a previous authenticated user.
 */
fun interface UserScopedDataCleaner {
    /**
     * Deletes all local rows owned by [userKey].
     */
    suspend fun deleteUserData(userKey: UserScopeKey): ResultWithError<Unit, LocalStorageError>
}
