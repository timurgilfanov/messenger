package timur.gilfanov.messenger.test

import java.util.UUID
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

object AndroidTestSettingsHelper {

    private const val DEVICE_ID = "00000000-0000-0000-0000-000000000001"

    val testUserId = UserId(UUID.fromString(AndroidTestDataHelper.USER_ID))

    val testIdentity = Identity(
        userId = testUserId,
        deviceId = DeviceId(UUID.fromString(DEVICE_ID)),
    )

    val defaultSettings = Settings(uiLanguage = UiLanguage.English)
}
