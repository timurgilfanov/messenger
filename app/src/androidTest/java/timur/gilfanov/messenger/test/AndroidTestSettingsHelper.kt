package timur.gilfanov.messenger.test

import java.util.UUID
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

object AndroidTestSettingsHelper {

    val testUserId = UserId(UUID.fromString(AndroidTestDataHelper.USER_ID))

    val testSession = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
        userId = testUserId,
    )

    val defaultSettings = Settings(uiLanguage = UiLanguage.English)
}
