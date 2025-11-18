package timur.gilfanov.messenger.data.source.remote

import java.util.UUID
import kotlin.test.assertEquals
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

        val remoteSettings = (result as ResultWithError.Success).data
        val uiLanguageItem = remoteSettings.uiLanguage
        assertEquals(1, (uiLanguageItem as RemoteSetting.Valid).serverVersion)
    }

    @Test
    fun `changeUiLanguage increments version from 1 to 2`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResultBefore = dataSource.get(testIdentity)
        val remoteSettingsBefore = (getResultBefore as ResultWithError.Success).data
        assertEquals(1, (remoteSettingsBefore.uiLanguage as RemoteSetting.Valid).serverVersion)

        dataSource.changeUiLanguage(testIdentity, UiLanguage.German)

        val getResultAfter = dataSource.get(testIdentity)
        val remoteSettingsAfter = (getResultAfter as ResultWithError.Success).data
        assertEquals(2, (remoteSettingsAfter.uiLanguage as RemoteSetting.Valid).serverVersion)
    }

    @Test
    fun `put increments version when value changes`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResultBefore = dataSource.get(testIdentity)
        val remoteSettingsBefore = (getResultBefore as ResultWithError.Success).data
        assertEquals(1, (remoteSettingsBefore.uiLanguage as RemoteSetting.Valid).serverVersion)

        val newSettings = Settings(uiLanguage = UiLanguage.German)
        dataSource.put(testIdentity, newSettings)

        val getResultAfter = dataSource.get(testIdentity)
        val remoteSettingsAfter = (getResultAfter as ResultWithError.Success).data
        assertEquals(2, (remoteSettingsAfter.uiLanguage as RemoteSetting.Valid).serverVersion)
    }

    @Test
    fun `put does not increment version when value stays the same`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResultBefore = dataSource.get(testIdentity)
        val remoteSettingsBefore = (getResultBefore as ResultWithError.Success).data
        assertEquals(1, (remoteSettingsBefore.uiLanguage as RemoteSetting.Valid).serverVersion)

        val sameSettings = Settings(uiLanguage = UiLanguage.English)
        dataSource.put(testIdentity, sameSettings)

        val getResultAfter = dataSource.get(testIdentity)
        val remoteSettingsAfter = (getResultAfter as ResultWithError.Success).data
        assertEquals(1, (remoteSettingsAfter.uiLanguage as RemoteSetting.Valid).serverVersion)
    }

    @Test
    fun `multiple changes increment version correctly`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        val getResult1 = dataSource.get(testIdentity)
        val remoteSettings1 = (getResult1 as ResultWithError.Success).data
        assertEquals(1, (remoteSettings1.uiLanguage as RemoteSetting.Valid).serverVersion)

        dataSource.changeUiLanguage(testIdentity, UiLanguage.German)

        val getResult2 = dataSource.get(testIdentity)
        val remoteSettings2 = (getResult2 as ResultWithError.Success).data
        assertEquals(2, (remoteSettings2.uiLanguage as RemoteSetting.Valid).serverVersion)

        dataSource.changeUiLanguage(testIdentity, UiLanguage.English)

        val getResult3 = dataSource.get(testIdentity)
        val remoteSettings3 = (getResult3 as ResultWithError.Success).data
        assertEquals(3, (remoteSettings3.uiLanguage as RemoteSetting.Valid).serverVersion)
    }

    @Test
    fun `put increments version on second change`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = persistentMapOf(testUserId to initialSettings),
        )

        dataSource.put(testIdentity, Settings(uiLanguage = UiLanguage.German))

        val getResult1 = dataSource.get(testIdentity)
        val remoteSettings1 = (getResult1 as ResultWithError.Success).data
        assertEquals(2, (remoteSettings1.uiLanguage as RemoteSetting.Valid).serverVersion)

        dataSource.put(testIdentity, Settings(uiLanguage = UiLanguage.English))

        val getResult2 = dataSource.get(testIdentity)
        val remoteSettings2 = (getResult2 as ResultWithError.Success).data
        assertEquals(3, (remoteSettings2.uiLanguage as RemoteSetting.Valid).serverVersion)
    }

    @Test
    fun `version tracking is isolated per user`() = runTest {
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
        val settings1 = (result1 as ResultWithError.Success).data
        assertEquals(2, (settings1.uiLanguage as RemoteSetting.Valid).serverVersion)

        val result2 = dataSource.get(identity2)
        val settings2 = (result2 as ResultWithError.Success).data
        assertEquals(1, (settings2.uiLanguage as RemoteSetting.Valid).serverVersion)
    }
}
