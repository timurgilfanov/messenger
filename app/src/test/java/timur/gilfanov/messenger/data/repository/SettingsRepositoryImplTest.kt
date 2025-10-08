package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.data.source.local.LocalSettingsDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteSettingsDataSourceFake
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

@Category(Unit::class)
class SettingsRepositoryImplTest {

    private val userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

    private val defaultUiLanguage = UiLanguage.English

    private val defaultSettings = persistentMapOf(userId to Settings(language = defaultUiLanguage))

    @Test
    fun `when change UI language then repository settings emit changes`() = runTest {
        val repository = SettingsRepositoryImpl(
            LocalSettingsDataSourceFake(defaultSettings),
            RemoteSettingsDataSourceFake(defaultSettings),
        )

        repository.observeSettings(userId).test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(initialResult)
            assertEquals(defaultUiLanguage, initialResult.data.language)
            expectNoEvents()

            val newUiLanguage = UiLanguage.German
            val changeLanguageResult = repository.changeLanguage(userId, newUiLanguage)
            assertIs<ResultWithError.Success<Unit, *>>(changeLanguageResult)

            val updatedResult = awaitItem()
            assertIs<ResultWithError.Success<Settings, *>>(updatedResult)
            assertEquals(newUiLanguage, updatedResult.data.language)
        }
    }
}
