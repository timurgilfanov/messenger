package timur.gilfanov.messenger.domain.entity.user

/**
 * Marker interface for all setting value types.
 *
 * Enables bounded type parameters in TypedLocalSetting and TypedSettingSyncRequest.
 *
 * All setting types (UiLanguage, Theme, Notifications) must implement this interface.
 */
sealed interface Setting
