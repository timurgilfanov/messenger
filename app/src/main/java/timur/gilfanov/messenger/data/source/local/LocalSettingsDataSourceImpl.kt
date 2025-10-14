package timur.gilfanov.messenger.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.data.source.errorReason
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsDataStoreManager
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsPreferences
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.bimap
import timur.gilfanov.messenger.domain.entity.foldWithErrorMapping
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.SettingsSource
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
    ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>> =
        dataStoreManager.getDataStore(userId).data
            .map { pref ->
                pref.toSettings().foldWithErrorMapping(
                    onSuccess = { settings ->
                        if (settings.metadata.source == SettingsSource.EMPTY) {
                            Failure(GetSettingsLocalDataSourceError.SettingsNotFound)
                        } else {
                            Success(settings)
                        }
                    },
                    onFailure = { GetSettingsLocalDataSourceError.LocalDataSource(it) },
                )
            }
            .catch { exception ->
                logger.e(TAG, "Error observing settings for user $userId", exception)
                emit(
                    Failure(
                        GetSettingsLocalDataSourceError.LocalDataSource(
                            when (exception) {
                                is IOException -> LocalDataSourceErrorV2.ReadError(
                                    exception.errorReason,
                                )

                                else -> LocalDataSourceErrorV2.DeserializationError(
                                    exception.errorReason,
                                )
                            },
                        ),
                    ),
                )
            }

    override suspend fun updateSettings(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError> {
        val dataStore = dataStoreManager.getDataStore(userId)
        return getSettings(dataStore).foldWithErrorMapping(
            onSuccess = { settings ->
                if (settings.metadata.source == SettingsSource.EMPTY) {
                    return@foldWithErrorMapping Failure(
                        UpdateSettingsLocalDataSourceError.SettingsNotFound,
                    )
                }

                val transformedSettings = try {
                    transform(settings)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    logger.e(TAG, "Settings transformation failed", e)
                    return@foldWithErrorMapping Failure(
                        UpdateSettingsLocalDataSourceError.TransformError(e),
                    )
                }
                val newSettings = transformedSettings.copy(
                    metadata = SettingsMetadata(
                        isDefault = settings.metadata.isDefault,
                        lastModifiedAt = Clock.System.now(),
                        lastSyncedAt = settings.metadata.lastSyncedAt,
                    ),
                )
                updateSettings(dataStore, newSettings).bimap(
                    onSuccess = { },
                    onFailure = { error ->
                        UpdateSettingsLocalDataSourceError.LocalDataSource(error)
                    },
                )
            },
            onFailure = { error ->
                UpdateSettingsLocalDataSourceError.LocalDataSource(error)
            },
        )
    }

    override suspend fun insertSettings(
        userId: UserId,
        settings: Settings,
    ): ResultWithError<Unit, InsertSettingsLocalDataSourceError> {
        val dataStore = dataStoreManager.getDataStore(userId)
        val now = Clock.System.now()
        val settingsWithMetadata = settings.copy(
            metadata = SettingsMetadata(
                isDefault = false,
                lastModifiedAt = now,
                lastSyncedAt = now,
            ),
        )
        return updateSettings(dataStore, settingsWithMetadata)
    }

    override suspend fun resetSettings(
        userId: UserId,
    ): ResultWithError<Unit, ResetSettingsLocalDataSourceError> {
        val dataStore = dataStoreManager.getDataStore(userId)
        val settingsWithMetadata = defaultSettings.copy(
            metadata = SettingsMetadata(
                isDefault = true,
                lastModifiedAt = Clock.System.now(),
                lastSyncedAt = null,
            ),
        )
        return updateSettings(dataStore, settingsWithMetadata)
    }

    private suspend fun getSettings(
        dataStore: DataStore<Preferences>,
    ): ResultWithError<Settings, LocalDataSourceErrorV2> {
        val preferences = try {
            dataStore.data.first()
        } catch (exception: IOException) {
            logger.e(TAG, "Storage read failed", exception)
            return Failure(LocalDataSourceErrorV2.ReadError(exception.errorReason))
        }
        return preferences.toSettings()
    }

    private fun Preferences.toSettings(): ResultWithError<
        Settings,
        LocalDataSourceErrorV2.DeserializationError,
        > =
        try {
            val uiLanguageString = this[UserSettingsPreferences.UI_LANGUAGE]
            val currentUiLanguage = uiLanguageString
                ?.let { parseUiLanguage(it) }
                ?: defaultSettings.language

            val isDefault = this[UserSettingsPreferences.METADATA_DEFAULT] ?: false
            val lastModifiedAtEpochMilli =
                this[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] ?: 0L
            val lastSyncedAtEpochMilli = this[UserSettingsPreferences.METADATA_LAST_SYNCED_AT]

            val metadata = SettingsMetadata(
                isDefault = isDefault,
                lastModifiedAt = Instant.fromEpochMilliseconds(lastModifiedAtEpochMilli),
                lastSyncedAt = lastSyncedAtEpochMilli?.let { Instant.fromEpochMilliseconds(it) },
            )

            Success(
                Settings(
                    language = currentUiLanguage,
                    metadata = metadata,
                ),
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") exception: Exception,
        ) {
            logger.e(TAG, "Settings deserialization failed", exception)
            Failure(LocalDataSourceErrorV2.DeserializationError(exception.errorReason))
        }

    private suspend fun updateSettings(
        dataStore: DataStore<Preferences>,
        newSettings: Settings,
    ): ResultWithError<Unit, LocalDataSourceErrorV2> = try {
        dataStore.edit { prefs ->
            prefs[UserSettingsPreferences.UI_LANGUAGE] = serializeUiLanguage(newSettings.language)
            prefs[UserSettingsPreferences.METADATA_DEFAULT] = newSettings.metadata.isDefault
            prefs[UserSettingsPreferences.METADATA_LAST_MODIFIED_AT] =
                newSettings.metadata.lastModifiedAt.toEpochMilliseconds()
            newSettings.metadata.lastSyncedAt?.let { syncedAt ->
                prefs[UserSettingsPreferences.METADATA_LAST_SYNCED_AT] =
                    syncedAt.toEpochMilliseconds()
            } ?: prefs.remove(UserSettingsPreferences.METADATA_LAST_SYNCED_AT)
        }
        Success(Unit)
    } catch (exception: IOException) {
        logger.e(TAG, "Update settings failed", exception)
        Failure(LocalDataSourceErrorV2.WriteError(exception.errorReason))
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") exception: Exception,
    ) {
        logger.e(TAG, "Settings transformation failed", exception)
        Failure(LocalDataSourceErrorV2.SerializationError(exception.errorReason))
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
