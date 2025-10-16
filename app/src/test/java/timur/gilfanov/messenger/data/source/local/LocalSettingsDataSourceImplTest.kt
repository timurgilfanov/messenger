package timur.gilfanov.messenger.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
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
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsPreferences
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.testutil.MainDispatcherRule

private const val ENGLISH_PREFERENCE = "en"

private const val GERMAN_PREFERENCE = "de"

private const val TEST_TIMESTAMP = 1L

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LocalSettingsDataSourceImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var dataStoreManager: UserSettingsDataStoreManagerFake
    private lateinit var dataSource: LocalSettingsDataSource

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val anotherUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreManager = UserSettingsDataStoreManagerFake(context)
        dataSource = LocalSettingsDataSourceImpl(
            dataStoreManager = dataStoreManager,
            logger = NoOpLogger(),
        )
    }

    @After
    fun tearDown() {
        dataStoreManager.clear(testUserId)
        dataStoreManager.clear(anotherUserId)
    }

    @Test
    fun `observeSettings returns SettingsNotFound when user has no settings`() = runTest {
        dataSource.observeSettings(testUserId).test {
            val result = awaitItem()

            assertIs<ResultWithError.Failure<*, GetSettingsLocalDataSourceError>>(result)
            assertEquals(GetSettingsLocalDataSourceError.SettingsNotFound, result.error)
        }
    }

    @Test
    fun `observeSettings returns Settings when user has settings`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        dataSource.observeSettings(testUserId).test {
            val result = awaitItem()

            assertIs<ResultWithError.Success<Settings, *>>(result)
            assertEquals(UiLanguage.English, result.data.uiLanguage)
        }
    }

    @Test
    fun `observeSettings emits updates when settings change`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        dataSource.observeSettings(testUserId).test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(firstResult)
            assertEquals(UiLanguage.English, firstResult.data.uiLanguage)

            dataStore.edit { prefs ->
                prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
            }

            val secondResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(secondResult)
            assertEquals(UiLanguage.German, secondResult.data.uiLanguage)
        }
    }

    @Test
    fun `observeSettings isolates different users`() = runTest {
        val dataStore1 = dataStoreManager.getDataStore(testUserId)
        val dataStore2 = dataStoreManager.getDataStore(anotherUserId)

        dataStore1.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }
        dataStore2.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        dataSource.observeSettings(testUserId).test {
            val result1 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result1)
            assertEquals(UiLanguage.English, result1.data.uiLanguage)
        }

        dataSource.observeSettings(anotherUserId).test {
            val result2 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result2)
            assertEquals(UiLanguage.German, result2.data.uiLanguage)
        }
    }

    @Test
    fun `updateSettings returns SettingsNotFound when user has no settings`() = runTest {
        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(uiLanguage = UiLanguage.German)
        }

        assertIs<ResultWithError.Failure<*, UpdateSettingsLocalDataSourceError>>(result)
        assertIs<UpdateSettingsLocalDataSourceError.SettingsNotFound>(result.error)
    }

    @Test
    fun `updateSettings successfully updates existing settings`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(uiLanguage = UiLanguage.German)
        }

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val updatedSettings = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(updatedSettings)
            assertEquals(UiLanguage.German, updatedSettings.data.uiLanguage)
        }
    }

    @Test
    fun `updateSettings applies transformation correctly`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        val result = dataSource.updateSettings(testUserId) { currentSettings ->
            assertEquals(UiLanguage.German, currentSettings.uiLanguage)
            currentSettings.copy(uiLanguage = UiLanguage.English)
        }

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val updatedSettings = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(updatedSettings)
            assertEquals(UiLanguage.English, updatedSettings.data.uiLanguage)
        }
    }

    @Test
    fun `updateSettings isolates different users`() = runTest {
        val dataStore1 = dataStoreManager.getDataStore(testUserId)
        val dataStore2 = dataStoreManager.getDataStore(anotherUserId)

        dataStore1.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }
        dataStore2.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(uiLanguage = UiLanguage.German)
        }

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val result1 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result1)
            assertEquals(UiLanguage.German, result1.data.uiLanguage)
        }

        dataSource.observeSettings(anotherUserId).test {
            val result2 = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(result2)
            assertEquals(UiLanguage.German, result2.data.uiLanguage)
        }
    }

    @Test
    fun `observeSettings defaults to English for unknown values`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = "unknown"
        }

        dataSource.observeSettings(testUserId).test {
            val result = awaitItem()

            assertIs<ResultWithError.Success<Settings, *>>(result)
            assertEquals(UiLanguage.English, result.data.uiLanguage)
        }
    }

    @Test
    fun `updateSettings returns TransformError when transformation throws exception`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        val result = dataSource.updateSettings(testUserId) {
            throw IllegalStateException("Transformation failed")
        }

        assertIs<ResultWithError.Failure<*, UpdateSettingsLocalDataSourceError>>(result)
        assertIs<UpdateSettingsLocalDataSourceError.TransformError>(result.error)
    }

    @Test
    fun `insertSettings successfully inserts new settings`() = runTest {
        val settingsToInsert = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(TEST_TIMESTAMP),
                lastSyncedAt = Instant.fromEpochMilliseconds(TEST_TIMESTAMP),
            ),
        )

        val result = dataSource.insertSettings(testUserId, settingsToInsert)

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val settings = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(settings)
            assertEquals(UiLanguage.German, settings.data.uiLanguage)
        }
    }

    @Test
    fun `resetSettings successfully resets to default settings`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        val result = dataSource.resetSettings(testUserId)

        assertIs<ResultWithError.Success<Unit, *>>(result)

        dataSource.observeSettings(testUserId).test {
            val settings = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(settings)
            assertEquals(UiLanguage.English, settings.data.uiLanguage)
            assertEquals(true, settings.data.metadata.isDefault)
        }
    }

    @Test
    fun `observeSettings returns ReadError when data store read failed`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        dataStoreManager.setReadError(testUserId)

        dataSource.observeSettings(testUserId).test {
            val result = awaitItem()

            assertIs<ResultWithError.Failure<*, GetSettingsLocalDataSourceError>>(result)
            assertIs<GetSettingsLocalDataSourceError.LocalDataSource>(result.error)
            assertIs<LocalDataSourceErrorV2.ReadError>(result.error.error)

            awaitComplete()
        }
    }

    @Test
    fun `updateSettings returns LocalDataSource WriteError when write fails`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        dataStoreManager.setWriteError(testUserId)

        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(uiLanguage = UiLanguage.German)
        }

        assertIs<ResultWithError.Failure<*, UpdateSettingsLocalDataSourceError>>(result)
        assertIs<UpdateSettingsLocalDataSourceError.UpdateSettingsLocalDataSource>(result.error)
        assertIs<LocalDataSourceErrorV2.WriteError>(result.error.error)
    }

    @Test
    fun `updateSettings returns LocalDataSource ReadError when read fails`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = ENGLISH_PREFERENCE
        }

        dataStoreManager.setReadError(testUserId)

        val result = dataSource.updateSettings(testUserId) { settings ->
            settings.copy(uiLanguage = UiLanguage.German)
        }

        assertIs<ResultWithError.Failure<*, UpdateSettingsLocalDataSourceError>>(result)
        assertIs<UpdateSettingsLocalDataSourceError.GetSettingsLocalDataSource>(result.error)
        assertIs<LocalDataSourceErrorV2.ReadError>(result.error.error)
    }

    @Test
    fun `insertSettings returns failure when DataStore write fails`() = runTest {
        val settingsToInsert = Settings(
            uiLanguage = UiLanguage.German,
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = Instant.fromEpochMilliseconds(TEST_TIMESTAMP),
                lastSyncedAt = Instant.fromEpochMilliseconds(TEST_TIMESTAMP),
            ),
        )

        dataStoreManager.setWriteError(testUserId)

        val result = dataSource.insertSettings(testUserId, settingsToInsert)

        assertIs<ResultWithError.Failure<*, InsertSettingsLocalDataSourceError>>(result)
        assertIs<LocalDataSourceErrorV2.WriteError>(result.error)
    }

    @Test
    fun `resetSettings returns failure when DataStore write fails`() = runTest {
        val dataStore = dataStoreManager.getDataStore(testUserId)
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] = TEST_TIMESTAMP
            prefs[UserSettingsPreferences.UI_LANGUAGE] = GERMAN_PREFERENCE
        }

        dataStoreManager.setWriteError(testUserId)

        val result = dataSource.resetSettings(testUserId)

        assertIs<ResultWithError.Failure<*, ResetSettingsLocalDataSourceError>>(result)
        assertIs<LocalDataSourceErrorV2.WriteError>(result.error)
    }
}
