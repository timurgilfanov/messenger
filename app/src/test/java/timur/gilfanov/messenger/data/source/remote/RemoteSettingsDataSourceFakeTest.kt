package timur.gilfanov.messenger.data.source.remote

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

@Category(Unit::class)
class RemoteSettingsDataSourceFakeTest {

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val testIdentity = Identity(
        userId = testUserId,
        deviceId = DeviceId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
    )

    @Test
    fun `get returns initial version 1 for all settings`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val result = dataSource.get(testIdentity)

        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(result)
        val remoteSettings = result.data
        val uiLanguageItem = remoteSettings.uiLanguage
        assertIs<RemoteSetting.Valid<UiLanguage>>(uiLanguageItem)
        assertEquals(1, uiLanguageItem.serverVersion)
    }

    @Test
    fun `changeUiLanguage increments version from 1 to 2`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResultBefore = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultBefore)
        val remoteSettingsBefore = getResultBefore.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsBefore.uiLanguage)
        assertEquals(1, remoteSettingsBefore.uiLanguage.serverVersion)

        dataSource.changeUiLanguage(testIdentity, UiLanguage.German)

        val getResultAfter = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(getResultAfter)
        val remoteSettingsAfter = getResultAfter.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsAfter.uiLanguage)
        assertEquals(2, remoteSettingsAfter.uiLanguage.serverVersion)
    }

    @Test
    fun `put increments version when value changes`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResultBefore = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultBefore)
        val remoteSettingsBefore = getResultBefore.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsBefore.uiLanguage)
        assertEquals(1, remoteSettingsBefore.uiLanguage.serverVersion)

        val newSettings = Settings(uiLanguage = UiLanguage.German)
        dataSource.put(testIdentity, newSettings)

        val getResultAfter = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultAfter)
        val remoteSettingsAfter = getResultAfter.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsAfter.uiLanguage)
        assertEquals(2, remoteSettingsAfter.uiLanguage.serverVersion)
    }

    @Test
    fun `put does not increment version when value stays the same`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResultBefore = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultBefore)
        val remoteSettingsBefore = getResultBefore.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsBefore.uiLanguage)
        assertEquals(1, remoteSettingsBefore.uiLanguage.serverVersion)

        val sameSettings = Settings(uiLanguage = UiLanguage.English)
        dataSource.put(testIdentity, sameSettings)

        val getResultAfter = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(getResultAfter)
        val remoteSettingsAfter = getResultAfter.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsAfter.uiLanguage)
        assertEquals(1, remoteSettingsAfter.uiLanguage.serverVersion)
    }

    @Test
    fun `multiple changes increment version correctly`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResult1 = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(getResult1)
        val remoteSettings1 = getResult1.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings1.uiLanguage)
        assertEquals(1, remoteSettings1.uiLanguage.serverVersion)

        dataSource.changeUiLanguage(testIdentity, UiLanguage.German)

        val getResult2 = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(getResult2)
        val remoteSettings2 = getResult2.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings2.uiLanguage)
        assertEquals(2, remoteSettings2.uiLanguage.serverVersion)

        dataSource.changeUiLanguage(testIdentity, UiLanguage.English)

        val getResult3 = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(getResult3)
        val remoteSettings3 = getResult3.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings3.uiLanguage)
        assertEquals(3, remoteSettings3.uiLanguage.serverVersion)
    }

    @Test
    fun `put increments version on second change`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        dataSource.put(testIdentity, Settings(uiLanguage = UiLanguage.German))

        val getResult1 = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(getResult1)
        val remoteSettings1 = getResult1.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings1.uiLanguage)
        assertEquals(2, remoteSettings1.uiLanguage.serverVersion)

        dataSource.put(testIdentity, Settings(uiLanguage = UiLanguage.English))

        val getResult2 = dataSource.get(testIdentity)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(getResult2)
        val remoteSettings2 = getResult2.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings2.uiLanguage)
        assertEquals(3, remoteSettings2.uiLanguage.serverVersion)
    }

    @Test
    fun `changing setting for one user do not affect other user`() = runTest {
        val user1 = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val user2 = UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val identity1 = Identity(
            userId = user1,
            deviceId = DeviceId(UUID.fromString("00000000-0000-0000-0000-000000000003")),
        )
        val identity2 = Identity(
            userId = user2,
            deviceId = DeviceId(UUID.fromString("00000000-0000-0000-0000-000000000004")),
        )

        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(
                user1 to Settings(uiLanguage = UiLanguage.English),
                user2 to Settings(uiLanguage = UiLanguage.English),
            ),
        )

        dataSource.changeUiLanguage(identity1, UiLanguage.German)

        val result1 = dataSource.get(identity1)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(result1)
        val settings1 = result1.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(settings1.uiLanguage)
        assertEquals(2, settings1.uiLanguage.serverVersion)

        val result2 = dataSource.get(identity2)
        assertIs<ResultWithError.Success<RemoteSettings, RemoteUserDataSourceError>>(result2)
        val settings2 = result2.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(settings2.uiLanguage)
        assertEquals(1, settings2.uiLanguage.serverVersion)
    }
}
