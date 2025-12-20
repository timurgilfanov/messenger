package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
object TestSettingsModule {

    private val defaultIdentity = Identity(
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        deviceId = DeviceId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
    )

    @Provides
    @Singleton
    fun provideIdentityRepository(): IdentityRepository =
        IdentityRepositoryStub(ResultWithError.Success(defaultIdentity))

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = SettingsRepositoryStub(
        ResultWithError.Success(
            Settings(uiLanguage = UiLanguage.English),
        ),
    )
}
