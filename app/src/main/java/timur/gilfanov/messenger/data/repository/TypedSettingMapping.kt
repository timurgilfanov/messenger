package timur.gilfanov.messenger.data.repository

import timur.gilfanov.messenger.data.source.local.LocalSetting
import timur.gilfanov.messenger.data.source.local.TypedLocalSetting
import timur.gilfanov.messenger.data.source.remote.SettingSyncRequest
import timur.gilfanov.messenger.data.source.remote.TypedSettingSyncRequest

/**
 * Extension function to convert [TypedLocalSetting] to [TypedSettingSyncRequest].
 *
 * This mapping belongs in the repository layer as it orchestrates between local and
 * remote data sources. Keeps data sources independent by avoiding cross-dependencies.
 *
 * Centralizes the conversion logic so there's only one place to update when adding
 * new setting types to both sealed interfaces.
 *
 * @return Typed sync request ready for remote data source
 */
fun TypedLocalSetting.toSyncRequest(): TypedSettingSyncRequest = when (this) {
    is TypedLocalSetting.UiLanguage -> TypedSettingSyncRequest.UiLanguage(
        setting.toSyncRequest(),
    )
}

private fun <T> LocalSetting<T>.toSyncRequest(): SettingSyncRequest<T> = SettingSyncRequest(
    value = this.value,
    clientVersion = this.localVersion,
    lastKnownServerVersion = this.serverVersion,
    modifiedAt = this.modifiedAt,
)
