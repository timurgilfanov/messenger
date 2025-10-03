package timur.gilfanov.messenger.domain.entity.user

data class Profile(val id: UserId, val name: String, val pictureUrl: String?) {
    override fun toString(): String = name
}
