package timur.gilfanov.messenger.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao

/**
 * Hilt module that provides Room database dependencies.
 *
 * Provides MessengerDatabase and its DAOs for local data persistence.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

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
}
