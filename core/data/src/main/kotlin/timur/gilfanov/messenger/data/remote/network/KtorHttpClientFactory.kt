package timur.gilfanov.messenger.data.remote.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds reusable Ktor networking primitives without depending on app configuration or DI.
 */
object KtorHttpClientFactory {

    private const val MAX_CONNECTIONS_COUNT = 100
    private const val REQUEST_TIMEOUT_MILLIS = 30_000L
    private const val CONNECT_TIMEOUT_MILLIS = 10_000L
    private const val SOCKET_TIMEOUT_MILLIS = 30_000L

    fun createJson(): Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun createEngine(): HttpClientEngine = CIO.create {
        maxConnectionsCount = MAX_CONNECTIONS_COUNT
        pipelining = true
        requestTimeout = REQUEST_TIMEOUT_MILLIS
    }

    fun create(
        engine: HttpClientEngine,
        json: Json,
        baseUrl: String,
        enableLogging: Boolean,
        logMessage: (String) -> Unit = {},
    ): HttpClient = HttpClient(engine) {
        defaultRequest {
            this.url(baseUrl)
            contentType(ContentType.Application.Json)
        }
        this.install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()
        }
        this.install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }
        this.install(ContentNegotiation) {
            json(json)
        }
        if (enableLogging) {
            this.install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        logMessage(message)
                    }
                }
                level = LogLevel.ALL
            }
        }
    }

    fun createContentNegotiationClient(json: Json): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }
}
