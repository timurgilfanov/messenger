package timur.gilfanov.messenger.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao

/**
 * Hilt module that provides database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Extension property for Context to get DataStore instance.
     * Creates a singleton DataStore for sync preferences.
     */
    private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "sync_preferences",
    )

    @Provides
    @Singleton
    fun provideMessengerDatabase(@ApplicationContext context: Context): MessengerDatabase =
        Room.databaseBuilder(
            context,
            MessengerDatabase::class.java,
            MessengerDatabase.DATABASE_NAME,
        ).build()

    @Provides
    @Singleton
    fun provideChatDao(database: MessengerDatabase): ChatDao = database.chatDao()

    @Provides
    @Singleton
    fun provideMessageDao(database: MessengerDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun provideParticipantDao(database: MessengerDatabase): ParticipantDao =
        database.participantDao()

    @Provides
    @Singleton
    @Named("sync")
    fun provideSyncDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.syncDataStore
}
