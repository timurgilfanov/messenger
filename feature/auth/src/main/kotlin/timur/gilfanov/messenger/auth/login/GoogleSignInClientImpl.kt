package timur.gilfanov.messenger.auth.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import timur.gilfanov.messenger.auth.R
import timur.gilfanov.messenger.util.Logger

class GoogleSignInClientImpl(private val logger: Logger) : GoogleSignInClient {

    override suspend fun signIn(context: Context): GoogleSignInResult {
        val credentialManager = CredentialManager.create(context)
        val request = GetCredentialRequest(
            listOf(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(R.string.google_server_client_id))
                    .build(),
            ),
        )
        return try {
            val response = credentialManager.getCredential(context, request)
            when (val credential = response.credential) {
                is CustomCredential -> parseCustomCredential(credential)
                else -> {
                    logger.e(TAG, "Unexpected type of credential")
                    GoogleSignInResult.Failed
                }
            }
        } catch (_: NoCredentialException) {
            GoogleSignInResult.Cancelled
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            logger.e(TAG, "Get credentials failed", e)
            GoogleSignInResult.Failed
        }
    }

    private fun parseCustomCredential(credential: CustomCredential): GoogleSignInResult {
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            logger.e(TAG, "Unexpected type of credential")
            return GoogleSignInResult.Failed
        }
        return try {
            val token = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInResult.Success(token.idToken)
        } catch (e: GoogleIdTokenParsingException) {
            logger.e(TAG, "Received an invalid google id token response", e)
            GoogleSignInResult.Failed
        }
    }

    private companion object {
        private const val TAG = "GoogleSignInClientImpl"
    }
}
