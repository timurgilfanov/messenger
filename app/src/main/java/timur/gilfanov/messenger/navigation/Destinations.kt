package timur.gilfanov.messenger.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ChatList : NavKey

@Serializable
data class Chat(val chatId: String) : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object ProfileEdit : NavKey

@Serializable
data object Language : NavKey

@Serializable
data object Login : NavKey
