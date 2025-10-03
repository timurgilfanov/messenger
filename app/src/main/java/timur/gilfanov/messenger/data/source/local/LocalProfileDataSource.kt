package timur.gilfanov.messenger.data.source.local

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Profile
import timur.gilfanov.messenger.domain.entity.user.UserId

interface LocalProfileDataSource {
    fun flowProfile(userId: UserId): Flow<ResultWithError<Profile, LocalProfileDataSourceError>>

    suspend fun insertProfile(
        profile: Profile,
    ): ResultWithError<Profile, LocalProfileDataSourceError>

    suspend fun updateName(
        userId: UserId,
        name: String,
    ): ResultWithError<Unit, LocalProfileDataSourceError>

    suspend fun updatePicture(
        userId: UserId,
        pictureUri: Uri,
    ): ResultWithError<Unit, LocalProfileDataSourceError>
}
