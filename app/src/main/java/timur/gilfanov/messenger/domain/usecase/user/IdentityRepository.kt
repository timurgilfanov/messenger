package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity

/**
 * Provides access to the current user's identity.
 *
 * The identity represents the authenticated user and their device,
 * which is required for performing user-specific operations.
 */
interface IdentityRepository {
    /**
     * Observes the current user's identity.
     *
     * Emits the current identity or an error if the identity cannot be retrieved.
     *
     * @return Flow emitting identity updates or errors
     */
    val identity: Flow<ResultWithError<Identity, GetIdentityError>>
}
