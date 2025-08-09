package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import timur.gilfanov.messenger.BuildConfig

/**
 * Qualifier for the base URL configuration
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl

/**
 * Qualifier for test/mock HTTP client
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MockHttpClient

/**
 * Dagger module for network-related dependencies.
 * Provides Ktor HTTP client configuration for remote data sources.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val MAX_CONNECTIONS_COUNT = 100
    private const val REQUEST_TIMEOUT_MILLIS = 30_000L
    private const val CONNECT_TIMEOUT_MILLIS = 10_000L
    private const val SOCKET_TIMEOUT_MILLIS = 30_000L

    /**
     * Provides the base URL for API endpoints.
     * Can be configured through BuildConfig for different environments.
     */
    @Provides
    @Singleton
    @BaseUrl
    fun provideBaseUrl(): String = BuildConfig.API_BASE_URL

    /**
     * Provides JSON configuration for serialization/deserialization.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /**
     * Provides the HTTP client engine.
     * CIO is used for Android compatibility.
     */
    @Provides
    @Singleton
    fun provideHttpClientEngine(): HttpClientEngine = CIO.create {
        // Connection configuration
        maxConnectionsCount = MAX_CONNECTIONS_COUNT
        pipelining = true

        // Request timeout configuration
        requestTimeout = REQUEST_TIMEOUT_MILLIS
    }

    /**
     * Provides the main HTTP client for production use.
     * Configured with retry, timeout, logging, and JSON content negotiation.
     */
    @Provides
    @Singleton
    fun provideHttpClient(
        engine: HttpClientEngine,
        json: Json,
        @BaseUrl baseUrl: String,
        logger: timur.gilfanov.messenger.util.Logger,
    ): HttpClient = HttpClient(engine) {
        // Default request configuration
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }

        // Retry configuration
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()
        }

        // Timeout configuration
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }

        // JSON content negotiation
        install(ContentNegotiation) {
            json(json)
        }

        // Logging configuration (only in debug builds)
        if (BuildConfig.DEBUG) {
            install(Logging) {
                this.logger = object : Logger {
                    override fun log(message: String) {
                        logger.d("NetworkModule", message)
                    }
                }
                level = LogLevel.ALL
            }
        }
    }

    /**
     * Provides a mock HTTP client for testing.
     * This can be used with MockEngine for integration tests.
     */
    @Provides
    @Named("mock")
    fun provideMockHttpClient(json: Json): HttpClient = HttpClient {
        // Basic configuration for mock client
        install(ContentNegotiation) {
            json(json)
        }
    }
}
