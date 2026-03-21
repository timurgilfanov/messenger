package timur.gilfanov.messenger.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshError
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshUseCase
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.onSuccess

/**
 * Ktor plugin that transparently handles authentication for all HTTP requests.
 *
 * ## Rules
 * 1. **Attach token** — if an access token is present in storage, it is added as
 *    `Authorization: Bearer <token>` before every request. Requests are sent without
 *    the header when no token is stored.
 * 2. **Transparent retry on 401** — a `401 Unauthorized` response triggers a token refresh.
 *    On success, the original request is retried once with the new token; the caller never
 *    observes the 401 or the refresh.
 * 3. **Return original 401 on refresh failure** — if the refresh fails for any reason,
 *    the original 401 response is returned to the caller unchanged.
 * 4. **Coalesce concurrent 401s** — when multiple requests receive a 401 simultaneously,
 *    exactly one refresh is performed; all callers wait for the same result.
 */
class AuthInterceptor(
    private val authSessionStorage: LocalAuthDataSource,
    private val tokenRefreshUseCase: TokenRefreshUseCase,
    private val scope: CoroutineScope,
) {
    private val refreshLock = Any()

    @Volatile
    private var ongoingRefresh: Deferred<ResultWithError<AuthTokens, TokenRefreshError>>? = null

    fun install(client: HttpClient) {
        client.plugin(HttpSend).intercept { request ->
            authSessionStorage.getAccessToken().onSuccess { accessToken ->
                if (accessToken != null) {
                    request.headers["Authorization"] = "Bearer $accessToken"
                }
            }

            val call = execute(request)

            if (call.response.status != HttpStatusCode.Unauthorized) {
                return@intercept call
            }

            val deferred = getOrCreateRefresh()
            val result = deferred.await()

            when (result) {
                is ResultWithError.Success -> {
                    authSessionStorage.getAccessToken().onSuccess { newToken ->
                        if (newToken != null) {
                            request.headers["Authorization"] = "Bearer $newToken"
                        }
                    }
                    execute(request)
                }

                is ResultWithError.Failure -> call
            }
        }
    }

    /**
     * Returns the in-flight refresh deferred if one exists, or starts a new one.
     *
     * The lock makes the read-then-create atomic across threads. The [Deferred.isActive] check
     * distinguishes a refresh that is still running (reuse it) from one that already completed —
     * without it, subsequent 401s would reuse the old deferred and no new refresh would be
     * triggered.
     */
    private fun getOrCreateRefresh(): Deferred<ResultWithError<AuthTokens, TokenRefreshError>> =
        synchronized(refreshLock) {
            val existing = ongoingRefresh
            if (existing != null && existing.isActive) {
                existing
            } else {
                scope.async { tokenRefreshUseCase() }.also { ongoingRefresh = it }
            }
        }
}
