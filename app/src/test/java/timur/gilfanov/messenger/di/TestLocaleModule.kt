package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.LocaleRepository

@Module
@InstallIn(SingletonComponent::class)
object TestLocaleModule {

    @Provides
    @Singleton
    fun provideLocaleRepository(): LocaleRepository = object : LocaleRepository {
        override fun applyLocale(language: UiLanguage) = Unit
        override fun getCurrentLocale(): UiLanguage? = null
    }

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
