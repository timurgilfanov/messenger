package timur.gilfanov.messenger.data.source.remote.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.serialization.json.Json

/**
 * Ktor client configuration for network communication.
 */
object KtorClient {

    private const val HTTP_SERVER_ERROR_END = 599
    private const val REQUEST_TIMEOUT_MS = 30_000L

    fun create(
        @Suppress("UnusedParameter") baseUrl: String = "https://api.messenger.example.com",
        enableLogging: Boolean = true,
    ): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()

            retryIf { request, response ->
                response.status.value in
                    HttpStatusCode.InternalServerError.value..HTTP_SERVER_ERROR_END ||
                    response.status == HttpStatusCode.TooManyRequests ||
                    response.status == HttpStatusCode.RequestTimeout
            }

            retryOnExceptionIf { request, cause ->
                cause is ConnectTimeoutException ||
                    cause is SocketTimeoutException ||
                    cause is IOException
            }
        }

        if (enableLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
                filter { request ->
                    request.url.host.contains("messenger")
                }
            }
        }

        engine {
            requestTimeout = REQUEST_TIMEOUT_MS
        }
    }
}
