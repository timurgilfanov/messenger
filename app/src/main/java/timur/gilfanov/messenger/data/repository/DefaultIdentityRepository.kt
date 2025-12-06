package timur.gilfanov.messenger.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.GetIdentityError
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository

/**
 * Temporary identity provider until authentication is implemented.
 *
 * Exposes a static identity so user-dependent features can function.
 */
@Singleton
class DefaultIdentityRepository @Inject constructor() : IdentityRepository {

    private val defaultIdentity = Identity(
        userId = UserId(UUID.fromString(DEFAULT_USER_ID)),
        deviceId = DeviceId(UUID.fromString(DEFAULT_DEVICE_ID)),
    )

    private val identityFlow =
        MutableStateFlow<ResultWithError<Identity, GetIdentityError>>(Success(defaultIdentity))

    override val identity: Flow<ResultWithError<Identity, GetIdentityError>>
        get() = identityFlow

    override fun getIdentity(userId: UserId): ResultWithError<Identity, GetIdentityError> =
        if (userId == defaultIdentity.userId) {
            Success(defaultIdentity)
        } else {
            Failure(Unit)
        }

    companion object {
        private const val DEFAULT_USER_ID = "550e8400-e29b-41d4-a716-446655440000"
        private const val DEFAULT_DEVICE_ID = "00000000-0000-0000-0000-000000000001"
    }
}
