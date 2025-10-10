package timur.gilfanov.messenger.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsDataStoreManager
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsPreferences
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.testutil.MainDispatcherRule

private const val ENGLISH_PREFERENCE = "en"

private const val GERMAN_PREFERENCE = "de"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LocalSettingsDataSourceImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var dataStoreManager: UserSettingsDataStoreManager
    private lateinit var dataSource: LocalSettingsDataSource

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val anotherUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreManager = UserSettingsDataStoreManager(context)
        dataSource = LocalSettingsDataSourceImpl(
            dataStoreManager = dataStoreManager,
            logger = NoOpLogger(),
        )
    }

    @After
    fun tearDown() {
        context.preferencesDataStoreFile("user_settings_${testUserId.id}").delete()
        context.preferencesDataStoreFile("user_settings_${anotherUserId.id}").delete()
    }

    @Test
    fun `observeSettings returns UserNotFound when user has no settings`() = runTest {
        dataSource.observeSettings(testUserId).test {
            val result = awaitItem()

            assertIs<ResultWithError.Failure<*, LocalUserDataSourceError>>(result)
            assertEquals(LocalUserDataSourceError.UserDataNotFound, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `observeSettings returns Settings when user has settings`() = runTest {
        val dataStore = dataStoreManager.getOrCreateDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        dataSource.observeSettings(testUserId).test {
            val result = awaitItem()

            assertIs<ResultWithError.Success<Settings, *>>(result)
            assertEquals(Settings(UiLanguage.English), result.data)
        }
    }

    @Test
    fun `observeSettings emits updates when settings change`() = runTest {
        val dataStore = dataStoreManager.getOrCreateDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        dataSource.observeSettings(testUserId).test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(firstResult)
            assertEquals(UiLanguage.English, firstResult.data.language)

            dataStore.edit { prefs ->
                prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
            }

            val secondResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(secondResult)
            assertEquals(UiLanguage.German, secondResult.data.language)
        }
    }

    @Test
    fun `observeSettings isolates different users`() = runTest {
        val dataStore1 = dataStoreManager.getOrCreateDataStore(testUserId)
        val dataStore2 = dataStoreManager.getOrCreateDataStore(anotherUserId)

        dataStore1.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }
        dataStore2.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        dataSource.observeSettings(testUserId).test {
            val result1 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result1)
            assertEquals(UiLanguage.English, result1.data.language)
        }

        dataSource.observeSettings(anotherUserId).test {
            val result2 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result2)
            assertEquals(UiLanguage.German, result2.data.language)
        }
    }

    @Test
    fun `updateSettings returns UserNotFound when user has no settings`() = runTest {
        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(language = UiLanguage.German)
        }

        assertIs<ResultWithError.Failure<*, UpdateSettingsLocalDataSourceError>>(result)
        assertIs<UpdateSettingsLocalDataSourceError.LocalUserDataSource>(result.error)
        assertEquals(LocalUserDataSourceError.UserDataNotFound, result.error.error)
    }

    @Test
    fun `updateSettings successfully updates existing settings`() = runTest {
        val dataStore = dataStoreManager.getOrCreateDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(language = UiLanguage.German)
        }

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val updatedSettings = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(updatedSettings)
            assertEquals(UiLanguage.German, updatedSettings.data.language)
        }
    }

    @Test
    fun `updateSettings applies transformation correctly`() = runTest {
        val dataStore = dataStoreManager.getOrCreateDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        val result = dataSource.updateSettings(testUserId) { currentSettings ->
            assertEquals(UiLanguage.German, currentSettings.language)
            currentSettings.copy(language = UiLanguage.English)
        }

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val updatedSettings = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(updatedSettings)
            assertEquals(UiLanguage.English, updatedSettings.data.language)
        }
    }

    @Test
    fun `updateSettings isolates different users`() = runTest {
        val dataStore1 = dataStoreManager.getOrCreateDataStore(testUserId)
        val dataStore2 = dataStoreManager.getOrCreateDataStore(anotherUserId)

        dataStore1.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }
        dataStore2.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(language = UiLanguage.German)
        }

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val result1 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result1)
            assertEquals(UiLanguage.German, result1.data.language)
        }

        dataSource.observeSettings(anotherUserId).test {
            val result2 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result2)
            assertEquals(UiLanguage.German, result2.data.language)
        }
    }

    @Test
    fun `observeSettings defaults to English for unknown values`() = runTest {
        val dataStore = dataStoreManager.getOrCreateDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = "unknown"
        }

        dataSource.observeSettings(testUserId).test {
            val result = awaitItem()

            assertIs<ResultWithError.Success<Settings, *>>(result)
            assertEquals(UiLanguage.English, result.data.language)
        }
    }
}
