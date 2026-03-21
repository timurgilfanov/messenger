package timur.gilfanov.messenger.auth.ui

import android.content.Context
import kotlinx.coroutines.CompletableDeferred

class GoogleSignInClientStub(
    var result: GoogleSignInResult = GoogleSignInResult.Cancelled,
    var shouldSuspend: Boolean = false,
) : GoogleSignInClient {
    private var pendingSignIn: CompletableDeferred<Unit>? = null

    fun reset() {
        result = GoogleSignInResult.Cancelled
        shouldSuspend = false
        pendingSignIn?.cancel()
        pendingSignIn = null
    }

    override suspend fun signIn(context: Context): GoogleSignInResult {
        if (shouldSuspend) {
            val deferred = CompletableDeferred<Unit>()
            pendingSignIn = deferred
            deferred.await()
        }
        return result
    }
}
