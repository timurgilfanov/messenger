package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

interface LocalProfileDataSource {
    fun observeProfile(userId: UserId): Flow<ResultWithError<Profile, LocalUserDataSourceError>>

    suspend fun updateProfile(
        userId: UserId,
        transform: (Profile) -> Profile,
    ): ResultWithError<Unit, LocalUserDataSourceError>
}
