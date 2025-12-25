package timur.gilfanov.messenger.data.source.remote

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class RemoteSettingsDataSourceFakeTest {

    @Test
    fun `get returns initial version 1 for all settings`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = initialSettings,
        )

        val result = dataSource.get()

        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(result)
        val remoteSettings = result.data
        val uiLanguageItem = remoteSettings.uiLanguage
        assertIs<RemoteSetting.Valid<UiLanguage>>(uiLanguageItem)
        assertEquals(UiLanguage.English, uiLanguageItem.value)
        assertEquals(1, uiLanguageItem.serverVersion)
    }

    @Test
    fun `changeUiLanguage increments version from 1 to 2`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = initialSettings,
        )

        val getResultBefore = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultBefore)
        val remoteSettingsBefore = getResultBefore.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsBefore.uiLanguage)
        assertEquals(UiLanguage.English, remoteSettingsBefore.uiLanguage.value)
        assertEquals(1, remoteSettingsBefore.uiLanguage.serverVersion)

        dataSource.changeUiLanguage(UiLanguage.German)

        val getResultAfter = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(
            getResultAfter,
        )
        val remoteSettingsAfter = getResultAfter.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsAfter.uiLanguage)
        assertEquals(UiLanguage.German, remoteSettingsAfter.uiLanguage.value)
        assertEquals(2, remoteSettingsAfter.uiLanguage.serverVersion)
    }

    @Test
    fun `put increments version when value changes`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = initialSettings,
        )

        val getResultBefore = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultBefore)
        val remoteSettingsBefore = getResultBefore.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsBefore.uiLanguage)
        assertEquals(UiLanguage.English, remoteSettingsBefore.uiLanguage.value)
        assertEquals(1, remoteSettingsBefore.uiLanguage.serverVersion)

        val newSettings = Settings(uiLanguage = UiLanguage.German)
        dataSource.put(newSettings)

        val getResultAfter = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultAfter)
        val remoteSettingsAfter = getResultAfter.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsAfter.uiLanguage)
        assertEquals(UiLanguage.German, remoteSettingsAfter.uiLanguage.value)
        assertEquals(2, remoteSettingsAfter.uiLanguage.serverVersion)
    }

    @Test
    fun `put does not increment version when value stays the same`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = initialSettings,
        )

        val getResultBefore = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, *>>(getResultBefore)
        val remoteSettingsBefore = getResultBefore.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsBefore.uiLanguage)
        assertEquals(UiLanguage.English, remoteSettingsBefore.uiLanguage.value)
        assertEquals(1, remoteSettingsBefore.uiLanguage.serverVersion)

        val sameSettings = Settings(uiLanguage = UiLanguage.English)
        dataSource.put(sameSettings)

        val getResultAfter = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(
            getResultAfter,
        )
        val remoteSettingsAfter = getResultAfter.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettingsAfter.uiLanguage)
        assertEquals(UiLanguage.English, remoteSettingsAfter.uiLanguage.value)
        assertEquals(1, remoteSettingsAfter.uiLanguage.serverVersion)
    }

    @Test
    fun `multiple changes increment version correctly`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = initialSettings,
        )

        val getResult1 = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(getResult1)
        val remoteSettings1 = getResult1.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings1.uiLanguage)
        assertEquals(UiLanguage.English, remoteSettings1.uiLanguage.value)
        assertEquals(1, remoteSettings1.uiLanguage.serverVersion)

        dataSource.changeUiLanguage(UiLanguage.German)

        val getResult2 = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(getResult2)
        val remoteSettings2 = getResult2.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings2.uiLanguage)
        assertEquals(UiLanguage.German, remoteSettings2.uiLanguage.value)
        assertEquals(2, remoteSettings2.uiLanguage.serverVersion)

        dataSource.changeUiLanguage(UiLanguage.English)

        val getResult3 = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(getResult3)
        val remoteSettings3 = getResult3.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings3.uiLanguage)
        assertEquals(UiLanguage.English, remoteSettings3.uiLanguage.value)
        assertEquals(3, remoteSettings3.uiLanguage.serverVersion)
    }

    @Test
    fun `put increments version on second change`() = runTest {
        val initialSettings = Settings(uiLanguage = UiLanguage.English)
        val dataSource = RemoteSettingsDataSourceFake(
            initialSettings = initialSettings,
        )

        dataSource.put(Settings(uiLanguage = UiLanguage.German))

        val getResult1 = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(getResult1)
        val remoteSettings1 = getResult1.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings1.uiLanguage)
        assertEquals(UiLanguage.German, remoteSettings1.uiLanguage.value)
        assertEquals(2, remoteSettings1.uiLanguage.serverVersion)

        dataSource.put(Settings(uiLanguage = UiLanguage.English))

        val getResult2 = dataSource.get()
        assertIs<ResultWithError.Success<RemoteSettings, RemoteSettingsDataSourceError>>(getResult2)
        val remoteSettings2 = getResult2.data
        assertIs<RemoteSetting.Valid<UiLanguage>>(remoteSettings2.uiLanguage)
        assertEquals(UiLanguage.English, remoteSettings2.uiLanguage.value)
        assertEquals(3, remoteSettings2.uiLanguage.serverVersion)
    }
}
