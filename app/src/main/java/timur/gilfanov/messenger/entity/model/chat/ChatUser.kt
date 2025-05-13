package timur.gilfanov.messenger.entity.model.chat

import java.util.UUID
import timur.gilfanov.messenger.entity.model.user.User

data class ChatUser(override val id: UUID, val name: String, val avatarUrl: String?) : User
