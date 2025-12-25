package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.settings.Setting

/**
 * Typed wrapper for setting synchronization requests to remote server.
 *
 * Provides type-safe sync requests by wrapping [SettingSyncRequest] with specific
 * domain types. Allows validation to happen at the remote data source boundary when
 * converting to wire format.
 *
 * Mirrors the pattern used by [timur.gilfanov.messenger.data.source.local.TypedLocalSetting]
 * for architectural consistency between local and remote data layers.
 *
 * ## Usage:
 * ```kotlin
 * val request = TypedSettingSyncRequest.UiLanguage(
 *     request = SettingSyncRequest(
 *         value = UiLanguage.ENGLISH,
 *         clientVersion = 2,
 *         lastKnownServerVersion = 1,
 *         modifiedAt = Clock.System.now()
 *     )
 * )
 * remoteDataSource.syncSingleSetting(request)
 * ```
 */
sealed interface TypedSettingSyncRequest {
    val request: SettingSyncRequest<out Setting>

    /**
     * UI language setting sync request.
     *
     * Wraps [SettingSyncRequest] containing typed [UiLanguage] value for remote synchronization.
     *
     * @property request The generic sync request with UiLanguage value and metadata
     */
    data class UiLanguage(
        override val request:
        SettingSyncRequest<timur.gilfanov.messenger.domain.entity.settings.UiLanguage>,
    ) : TypedSettingSyncRequest
}
