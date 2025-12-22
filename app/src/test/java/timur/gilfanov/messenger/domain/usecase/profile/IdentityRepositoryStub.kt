package timur.gilfanov.messenger.domain.usecase.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId

class IdentityRepositoryStub(
    identityFlow: Flow<ResultWithError<Identity, GetIdentityError>>,
    private val getIdentityResult: ResultWithError<Identity, GetIdentityError>? = null,
) : IdentityRepository {

    constructor(identityResult: ResultWithError<Identity, GetIdentityError>) : this(
        identityFlow = flowOf(identityResult),
        getIdentityResult = identityResult,
    )

    override val identity: Flow<ResultWithError<Identity, GetIdentityError>> = identityFlow

    override suspend fun getIdentity(userId: UserId): ResultWithError<Identity, GetIdentityError> =
        getIdentityResult ?: ResultWithError.Failure(GetIdentityError)
}
