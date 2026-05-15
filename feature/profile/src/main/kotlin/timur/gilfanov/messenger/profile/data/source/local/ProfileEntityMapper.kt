package timur.gilfanov.messenger.profile.data.source.local

import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.profile.Profile

fun ProfileEntity.toDomain(): Profile = Profile(
    name = name,
    pictureUrl = pictureUrl,
)

fun Profile.toEntity(userKey: UserScopeKey): ProfileEntity = ProfileEntity(
    userKey = userKey.key,
    name = name,
    pictureUrl = pictureUrl,
)
