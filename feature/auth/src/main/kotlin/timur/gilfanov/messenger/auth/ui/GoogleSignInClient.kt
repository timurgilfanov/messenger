package timur.gilfanov.messenger.auth.ui

import android.content.Context

sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object Failed : GoogleSignInResult
}

fun interface GoogleSignInClient {
    suspend fun signIn(context: Context): GoogleSignInResult
}
