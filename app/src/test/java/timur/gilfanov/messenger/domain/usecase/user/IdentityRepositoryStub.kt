package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.UserId

class IdentityRepositoryStub(identityFlow: Flow<ResultWithError<Identity, GetIdentityError>>) :
    IdentityRepository {

    constructor(identityResult: ResultWithError<Identity, GetIdentityError>) : this(
        flowOf(
            identityResult,
        ),
    )

    override val identity: Flow<ResultWithError<Identity, GetIdentityError>> = identityFlow

    override fun getIdentity(userId: UserId): ResultWithError<Identity, GetIdentityError> =
        ResultWithError.Failure(GetIdentityError)
}
