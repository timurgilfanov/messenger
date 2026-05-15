package timur.gilfanov.messenger.profile.data.source.remote

import kotlinx.serialization.Serializable
import timur.gilfanov.messenger.domain.entity.profile.Profile

@Serializable
data class ProfileDto(val name: String, val pictureUrl: String? = null) {
    fun toDomain(): Profile = Profile(
        name = name,
        pictureUrl = pictureUrl,
    )
}
