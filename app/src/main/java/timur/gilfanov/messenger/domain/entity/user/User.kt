package timur.gilfanov.messenger.domain.entity.user

import java.util.UUID

@JvmInline
value class UserId(val id: UUID)

data class User(val id: UserId, val name: String, val pictureUrl: String?) {
    override fun toString(): String = name
}
