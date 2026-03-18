package timur.gilfanov.messenger.auth.login

import android.content.Context

class GoogleSignInClientStub(var result: GoogleSignInResult = GoogleSignInResult.Cancelled) :
    GoogleSignInClient {
    override suspend fun signIn(context: Context): GoogleSignInResult = result
}
