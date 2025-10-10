package timur.gilfanov.messenger.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsDataStoreManager
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsPreferences
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.util.Logger

class LocalSettingsDataSourceImpl @Inject constructor(
    private val dataStoreManager: UserSettingsDataStoreManager,
    private val logger: Logger,
) : LocalSettingsDataSource {

    companion object {
        private const val TAG = "LocalSettingsDataSource"
    }

    private val defaultSettings = Settings(language = UiLanguage.English)

    override fun observeSettings(
        userId: UserId,
    ): Flow<ResultWithError<Settings, LocalUserDataSourceError>> {
        val dataStore = dataStoreManager.getDataStore(userId)
        if (dataStore == null) {
            return flowOf(
                Failure(LocalUserDataSourceError.UserDataNotFound),
            )
        }

        return dataStore.data
            .map<Preferences, ResultWithError<Settings, LocalUserDataSourceError>> { preferences ->
                Success(getSettings(preferences))
            }
            .catch { exception ->
                logger.e(TAG, "Error observing settings for user $userId", exception)
                when (exception) {
                    is IOException -> emit(
                        Failure(
                            LocalUserDataSourceError.LocalDataSource(
                                LocalDataSourceErrorV2.ReadError(exception),
                            ),
                        ),
                    )

                    else -> emit(
                        Failure(
                            LocalUserDataSourceError.LocalDataSource(
                                LocalDataSourceErrorV2.DataCorrupted,
                            ),
                        ),
                    )
                }
            }
    }

    override suspend fun updateSettings(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> {
        val dataStore = dataStoreManager.getDataStore(userId)
        if (dataStore == null) {
            return Failure(
                UpdateSettingsLocalDataSourceError.LocalUserDataSource(
                    LocalUserDataSourceError.UserDataNotFound,
                ),
            )
        }

        return getSettings(dataStore).foldWithErrorMapping(
            onSuccess = { settings ->
                val newSettings = try {
                    transform(settings)
                } catch (
                    // We don't control what inside transformation function
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    logger.e(TAG, "", e)
                    return@foldWithErrorMapping Failure(
                        UpdateSettingsLocalDataSourceError.TransformError(e),
                    )
                }
                updateSettings(dataStore, newSettings).bimap(
                    onSuccess = { },
                    onFailure = { error ->
                        UpdateSettingsLocalDataSourceError.LocalUserDataSource(
                            LocalUserDataSourceError.LocalDataSource(error),
                        )
                    },
                )
            },
            onFailure = { error ->
                UpdateSettingsLocalDataSourceError.LocalUserDataSource(
                    LocalUserDataSourceError.LocalDataSource(error),
                )
            },
        )
    }

    override suspend fun insertSettings(
        userId: UserId,
        settings: Settings,
    ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> {
        val dataStore = dataStoreManager.getOrCreateDataStore(userId)
        return updateSettings(dataStore, settings)
    }

    override suspend fun resetSettings(
        userId: UserId,
    ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> =
        insertSettings(userId, defaultSettings)

    private suspend fun getSettings(
        dataStore: DataStore<Preferences>,
    ): ResultWithError<Settings, LocalDataSourceErrorV2> {
        val preferences = try {
            dataStore.data.first()
        } catch (exception: IOException) {
            logger.e(TAG, "Storage read failed", exception)
            return Failure(LocalDataSourceErrorV2.ReadError(exception))
        }
        val currentSettings = getSettings(preferences)
        return Success(currentSettings)
    }

    private fun getSettings(preferences: Preferences): Settings {
        val uiLanguageString = preferences[UserSettingsPreferences.UI_LANGUAGE]
        val currentUiLanguage = uiLanguageString
            ?.let { parseUiLanguage(it) }
            ?: defaultSettings.language
        return Settings(language = currentUiLanguage)
    }

    private suspend fun updateSettings(
        dataStore: DataStore<Preferences>,
        newSettings: Settings,
    ): ResultWithError<Unit, LocalDataSourceErrorV2> = try {
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = serializeUiLanguage(newSettings.language)
        }
        Success(Unit)
    } catch (exception: IOException) {
        logger.e(TAG, "", exception)
        Failure(LocalDataSourceErrorV2.WriteError(exception))
    } catch (e: CancellationException) {
        throw e
    } catch (
        // In case transformation throw an exception
        @Suppress("TooGenericExceptionCaught") exception: Exception,
    ) {
        logger.e(TAG, "Settings transformation failed", exception)
        Failure(LocalDataSourceErrorV2.SerializationError(exception))
    }

    private fun parseUiLanguage(value: String): UiLanguage = when (value) {
        "en" -> UiLanguage.English
        "de" -> UiLanguage.German
        else -> UiLanguage.English
    }

    private fun serializeUiLanguage(language: UiLanguage): String = when (language) {
        UiLanguage.English -> "en"
        UiLanguage.German -> "de"
    }
}
