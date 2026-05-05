package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import timur.gilfanov.messenger.BuildConfig
import timur.gilfanov.messenger.auth.AuthInterceptor
import timur.gilfanov.messenger.auth.di.UnauthenticatedHttpClient
import timur.gilfanov.messenger.data.remote.network.KtorHttpClientFactory

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
    fun provideJson(): Json = KtorHttpClientFactory.createJson()

    /**
     * Provides the HTTP client engine.
     * CIO is used for Android compatibility.
     */
    @Provides
    @Singleton
    fun provideHttpClientEngine(): HttpClientEngine = KtorHttpClientFactory.createEngine()

    /**
     * Provides the main HTTP client for production use.
     * Configured with retry, timeout, logging, and JSON content negotiation.
     * Includes authentication interceptor for automatic token injection.
     */
    @Provides
    @Singleton
    fun provideHttpClient(
        engine: HttpClientEngine,
        json: Json,
        @BaseUrl baseUrl: String,
        logger: timur.gilfanov.messenger.util.Logger,
        authInterceptor: AuthInterceptor,
    ): HttpClient = createHttpClient(engine, json, baseUrl, logger).also {
        authInterceptor.install(it)
    }

    /**
     * Provides an HTTP client without authentication interceptor.
     * Used by RemoteAuthDataSource to avoid infinite recursion when refreshing tokens.
     * Shares the same configuration as the main HTTP client (engine, JSON, baseUrl, timeouts, logging)
     * but omits AuthInterceptor.install().
     */
    @Provides
    @Singleton
    @UnauthenticatedHttpClient
    fun provideUnauthenticatedHttpClient(
        engine: HttpClientEngine,
        json: Json,
        @BaseUrl baseUrl: String,
        logger: timur.gilfanov.messenger.util.Logger,
    ): HttpClient = createHttpClient(engine, json, baseUrl, logger)

    private fun createHttpClient(
        engine: HttpClientEngine,
        json: Json,
        baseUrl: String,
        logger: timur.gilfanov.messenger.util.Logger,
    ): HttpClient = KtorHttpClientFactory.create(
        engine = engine,
        json = json,
        baseUrl = baseUrl,
        enableLogging = BuildConfig.DEBUG,
    ) { message ->
        logger.d("NetworkModule", message)
    }

    /**
     * Provides a mock HTTP client for testing.
     * This can be used with MockEngine for integration tests.
     */
    @Provides
    @Named("mock")
    fun provideMockHttpClient(json: Json): HttpClient =
        KtorHttpClientFactory.createContentNegotiationClient(json)
}
