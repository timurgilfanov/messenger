package timur.gilfanov.messenger.data.source.local

import java.util.UUID
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

@Category(Unit::class)
class LocalSettingsTest {

    private val testUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @Test
    fun `fromEntities maps valid UI_LANGUAGE entity correctly`() {
        // Given
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 5,
            syncedVersion = 3,
            serverVersion = 2,
            modifiedAt = 1234567890L,
            syncStatus = SyncStatus.PENDING,
        )

        // When
        val result = LocalSettings.fromEntities(
            listOf(entity),
            defaults = Settings(uiLanguage = UiLanguage.English),
        )

        // Then
        assertEquals(UiLanguage.German, result.uiLanguage.value)
        assertEquals(5, result.uiLanguage.localVersion)
        assertEquals(3, result.uiLanguage.syncedVersion)
        assertEquals(2, result.uiLanguage.serverVersion)
        assertEquals(1234567890L, result.uiLanguage.modifiedAt)
        assertEquals(SyncStatus.PENDING, result.uiLanguage.syncStatus)
    }

    @Test
    fun `fromEntities uses default English when UI_LANGUAGE entity missing`() {
        // Given - empty list, no UI_LANGUAGE entity
        val entities = emptyList<SettingEntity>()

        // When
        val result = LocalSettings.fromEntities(
            entities,
            defaults = Settings(uiLanguage = UiLanguage.English),
        )

        // Then
        assertEquals(UiLanguage.English, result.uiLanguage.value)
        assertEquals(1, result.uiLanguage.localVersion)
        assertEquals(0, result.uiLanguage.syncedVersion)
        assertEquals(0, result.uiLanguage.serverVersion)
        assertEquals(SyncStatus.PENDING, result.uiLanguage.syncStatus)
    }

    @Test
    fun `fromEntities falls back to English when value is invalid`() {
        // Given
        val entity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "InvalidLanguage", // Invalid value
            localVersion = 2,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )

        // When
        val result = LocalSettings.fromEntities(
            listOf(entity),
            defaults = Settings(uiLanguage = UiLanguage.English),
        )

        // Then
        assertEquals(UiLanguage.English, result.uiLanguage.value, "Should fall back to English")
        assertEquals(2, result.uiLanguage.localVersion)
        assertEquals(1, result.uiLanguage.syncedVersion)
    }

    @Test
    fun `fromEntities ignores non-UI_LANGUAGE entities`() {
        // Given
        val themeEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.THEME.key,
            value = "DARK",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )

        // When
        val result = LocalSettings.fromEntities(
            listOf(themeEntity),
            defaults = Settings(uiLanguage = UiLanguage.English),
        )

        // Then - should use default English since UI_LANGUAGE not present
        assertEquals(UiLanguage.English, result.uiLanguage.value)
    }

    @Test
    fun `fromEntities handles multiple entities correctly`() {
        // Given
        val uiLanguageEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 2,
            syncedVersion = 2,
            serverVersion = 2,
            modifiedAt = 2000L,
            syncStatus = SyncStatus.SYNCED,
        )
        val themeEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.THEME.key,
            value = "LIGHT",
            localVersion = 1,
            syncedVersion = 1,
            serverVersion = 1,
            modifiedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
        )

        // When
        val result = LocalSettings.fromEntities(
            listOf(uiLanguageEntity, themeEntity),
            defaults = Settings(uiLanguage = UiLanguage.English),
        )

        // Then - should use the UI_LANGUAGE entity
        assertEquals(UiLanguage.German, result.uiLanguage.value)
        assertEquals(2, result.uiLanguage.localVersion)
    }

    @Test
    fun `toSettingEntities converts LocalSettings back to entities`() {
        // Given
        val localSettings = LocalSettings(
            uiLanguage = LocalSetting(
                value = UiLanguage.German,
                localVersion = 5,
                syncedVersion = 3,
                serverVersion = 2,
                modifiedAt = 1234567890L,
                syncStatus = SyncStatus.PENDING,
            ),
        )

        // When
        val entities = localSettings.toSettingEntities(testUserId)

        // Then
        assertEquals(1, entities.size)
        val entity = entities[0]
        assertEquals(testUserId.id.toString(), entity.userId)
        assertEquals(SettingKey.UI_LANGUAGE.key, entity.key)
        assertEquals("German", entity.value)
        assertEquals(5, entity.localVersion)
        assertEquals(3, entity.syncedVersion)
        assertEquals(2, entity.serverVersion)
        assertEquals(1234567890L, entity.modifiedAt)
        assertEquals(SyncStatus.PENDING, entity.syncStatus)
    }

    @Test
    fun `toDomain converts LocalSettings to domain Settings`() {
        // Given
        val localSettings = LocalSettings(
            uiLanguage = LocalSetting(
                value = UiLanguage.German,
                localVersion = 5,
                syncedVersion = 3,
                serverVersion = 2,
                modifiedAt = 1234567890L,
                syncStatus = SyncStatus.PENDING,
            ),
        )

        // When
        val domainSettings = localSettings.toDomain()

        // Then
        assertEquals(UiLanguage.German, domainSettings.uiLanguage)
    }

    @Test
    fun `round trip conversion preserves data`() {
        // Given
        val originalEntity = SettingEntity(
            userId = testUserId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = "German",
            localVersion = 5,
            syncedVersion = 3,
            serverVersion = 2,
            modifiedAt = 1234567890L,
            syncStatus = SyncStatus.PENDING,
        )

        // When - convert entity -> LocalSettings -> entity
        val localSettings = LocalSettings.fromEntities(
            listOf(originalEntity),
            defaults = Settings(uiLanguage = UiLanguage.English),
        )
        val roundTripEntities = localSettings.toSettingEntities(testUserId)

        // Then
        assertEquals(1, roundTripEntities.size)
        val roundTripEntity = roundTripEntities[0]
        assertEquals(originalEntity.userId, roundTripEntity.userId)
        assertEquals(originalEntity.key, roundTripEntity.key)
        assertEquals(originalEntity.value, roundTripEntity.value)
        assertEquals(originalEntity.localVersion, roundTripEntity.localVersion)
        assertEquals(originalEntity.syncedVersion, roundTripEntity.syncedVersion)
        assertEquals(originalEntity.serverVersion, roundTripEntity.serverVersion)
        assertEquals(originalEntity.modifiedAt, roundTripEntity.modifiedAt)
        assertEquals(originalEntity.syncStatus, roundTripEntity.syncStatus)
    }
}
