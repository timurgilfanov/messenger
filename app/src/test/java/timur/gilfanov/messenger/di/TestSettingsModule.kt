package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
object TestSettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = SettingsRepositoryStub(
        timur.gilfanov.messenger.domain.entity.ResultWithError.Success(
            Settings(uiLanguage = UiLanguage.English),
        ),
    )
}
