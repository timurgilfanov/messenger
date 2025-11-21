package timur.gilfanov.messenger.data.repository

import timur.gilfanov.messenger.data.source.local.TypedLocalSetting
import timur.gilfanov.messenger.data.source.remote.SettingSyncRequest
import timur.gilfanov.messenger.data.source.remote.TypedSettingSyncRequest
import timur.gilfanov.messenger.domain.entity.user.Identity

/**
 * Extension function to convert [TypedLocalSetting] to [TypedSettingSyncRequest].
 *
 * This mapping belongs in the repository layer as it orchestrates between local and
 * remote data sources. Keeps data sources independent by avoiding cross-dependencies.
 *
 * Centralizes the conversion logic so there's only one place to update when adding
 * new setting types to both sealed interfaces.
 *
 * @param identity The user identity for which the setting is being synced
 * @return Typed sync request ready for remote data source
 */
fun TypedLocalSetting.toSyncRequest(identity: Identity): TypedSettingSyncRequest = when (this) {
    is TypedLocalSetting.UiLanguage -> TypedSettingSyncRequest.UiLanguage(
        request = SettingSyncRequest(
            identity = identity,
            value = this.setting.value,
            clientVersion = this.setting.localVersion,
            lastKnownServerVersion = this.setting.serverVersion,
            modifiedAt = this.setting.modifiedAt,
        ),
    )
}
