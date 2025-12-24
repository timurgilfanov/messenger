package timur.gilfanov.messenger.test

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.data.repository.SettingsRepositoryImpl
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceImpl
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError
import timur.gilfanov.messenger.util.Logger

class AndroidTestSettingsRepository(private val logger: Logger = TestLogger()) :
    SettingsRepository,
    Closeable {

    private val instanceId = Integer.toHexString(System.identityHashCode(this))

    val remoteDataSourceFake: RemoteSettingsDataSourceFake

    private val repositoryScope: CoroutineScope
    private val realRepository: SettingsRepositoryImpl

    init {
        logger.d(TAG, "Creating settings repository instance: $instanceId")

        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

        val database = Room.inMemoryDatabaseBuilder(context, MessengerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val localDataSource = LocalSettingsDataSourceImpl(
            database = database,
            settingsDao = database.settingsDao(),
            logger = logger,
            defaultSettings = AndroidTestSettingsHelper.defaultSettings,
        )

        remoteDataSourceFake = RemoteSettingsDataSourceFake(
            initialSettings = AndroidTestSettingsHelper.defaultSettings,
            useRealTime = false,
            logger = logger,
        )

        val syncScheduler = SettingsSyncSchedulerStub()

        repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        realRepository = SettingsRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSourceFake,
            syncScheduler = syncScheduler,
            logger = logger,
            defaultSettings = AndroidTestSettingsHelper.defaultSettings,
        )

        logger.d(TAG, "Settings repository instance $instanceId created successfully")
    }

    override fun close() {
        logger.d(TAG, "Closing settings repository instance: $instanceId")
        repositoryScope.cancel()
        logger.d(TAG, "Settings repository instance $instanceId closed")
    }

    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        realRepository.observeSettings(identity)

    override fun observeConflicts(): Flow<SettingsConflictEvent> = realRepository.observeConflicts()

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> =
        realRepository.changeUiLanguage(identity, language)

    override suspend fun syncSetting(
        identity: Identity,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> = realRepository.syncSetting(identity, key)

    override suspend fun syncAllPendingSettings(
        identity: Identity,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> =
        realRepository.syncAllPendingSettings(identity)

    companion object {
        private const val TAG = "AndroidTestSettingsRepo"
    }
}
