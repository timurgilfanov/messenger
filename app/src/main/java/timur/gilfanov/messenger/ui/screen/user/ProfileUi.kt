package timur.gilfanov.messenger.ui.screen.user

import android.net.Uri

/**
 * UI model for user profile display.
 *
 * Represents user profile data in a UI-friendly format with Android-specific
 * types (Uri for pictures).
 *
 * @property name User's display name
 * @property picture URI to user's profile picture, null if no picture is set
 */
data class ProfileUi(val name: String, val picture: Uri?)
