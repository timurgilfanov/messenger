package timur.gilfanov.messenger.test

import java.util.UUID
import kotlinx.collections.immutable.persistentMapOf
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

object AndroidTestSettingsHelper {

    private const val DEVICE_ID = "00000000-0000-0000-0000-000000000001"

    val testUserId = UserId(UUID.fromString(AndroidTestDataHelper.USER_ID))

    val testIdentity = Identity(
        userId = testUserId,
        deviceId = DeviceId(UUID.fromString(DEVICE_ID)),
    )

    val defaultSettings = Settings(uiLanguage = UiLanguage.English)

    val initialRemoteSettings = persistentMapOf(testUserId to defaultSettings)
}
