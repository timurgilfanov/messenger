package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timur.gilfanov.messenger.data.repository.DefaultIdentityRepository
import timur.gilfanov.messenger.data.repository.LocaleRepositoryImpl
import timur.gilfanov.messenger.data.repository.MessengerRepositoryImpl
import timur.gilfanov.messenger.data.repository.SettingsRepositoryImpl
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindChatRepository(
        messengerRepositoryImpl: MessengerRepositoryImpl,
    ): ChatRepository

    @Binds
    abstract fun bindMessageRepository(
        messengerRepositoryImpl: MessengerRepositoryImpl,
    ): MessageRepository

    @Binds
    abstract fun bindIdentityRepository(
        defaultIdentityRepository: DefaultIdentityRepository,
    ): IdentityRepository

    @Binds
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl,
    ): SettingsRepository

    @Binds
    abstract fun bindLocaleRepository(localeRepositoryImpl: LocaleRepositoryImpl): LocaleRepository

    companion object {
        @Provides
        @Singleton
        fun provideRepositoryScope(): CoroutineScope = CoroutineScope(SupervisorJob())
    }
}
