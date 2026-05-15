package timur.gilfanov.messenger.profile.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val userKey: String,
    val name: String,
    val pictureUrl: String?,
)
