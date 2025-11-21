package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Provides access to the identities.
 *
 * The identity represents the authenticated user and their device,
 * which is required for performing user-specific operations.
 */
interface IdentityRepository {
    /**
     * Observes the current identity.
     *
     * Emits the current identity or an error if the identity cannot be retrieved.
     *
     * @return Flow emitting identity updates or errors
     */
    val identity: Flow<ResultWithError<Identity, GetIdentityError>>

    /**
     * Retrieves identity by user ID.
     *
     * @param userId The user ID for which to retrieve the identity.
     */
    fun getIdentity(userId: UserId): ResultWithError<Identity, GetIdentityError>
}
