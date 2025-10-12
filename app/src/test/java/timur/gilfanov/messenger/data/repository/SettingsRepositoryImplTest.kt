package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsMetadata
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SettingsRepositoryImplTest {

    private val identity = Identity(
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        deviceId = DeviceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
    )

    private val defaultUiLanguage = UiLanguage.English

    private val defaultSettings =
        persistentMapOf(
            identity.userId to Settings(
                language = defaultUiLanguage,
                metadata = SettingsMetadata(
                    isDefault = false,
                    lastModifiedAt = Instant.fromEpochMilliseconds(1),
                    lastSyncedAt = Instant.fromEpochMilliseconds(1),
                ),
            ),
        )

    private val repository = SettingsRepositoryImpl(
        localDataSource = LocalSettingsDataSourceFake(defaultSettings),
        remoteDataSource = RemoteSettingsDataSourceFake(defaultSettings),
        logger = NoOpLogger(),
    )

    @Test
    fun `when change UI language then repository settings emit changes`() = runTest {
        repository.observeSettings(identity).test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(initialResult)
            assertEquals(defaultUiLanguage, initialResult.data.language)
            expectNoEvents()

            val newUiLanguage = UiLanguage.German
            val changeLanguageResult = repository.changeLanguage(identity, newUiLanguage)
            assertIs<ResultWithError.Success<Unit, *>>(changeLanguageResult)

            val updatedResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(updatedResult)
            assertEquals(newUiLanguage, updatedResult.data.language)
        }
    }
}
