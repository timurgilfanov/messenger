package timur.gilfanov.messenger.domain.entity.user

/**
 * User profile information.
 *
 * Represents publicly visible user data that can be updated by the user.
 *
 * @property id Unique identifier for the user
 * @property name Display name (validated via [timur.gilfanov.messenger.domain.usecase.user.UpdateNameError])
 * @property pictureUrl URL to user's profile picture, null if no picture set
 */
data class Profile(val id: UserId, val name: String, val pictureUrl: String?) {
    override fun toString(): String = name
}
