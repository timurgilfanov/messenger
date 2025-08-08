package timur.gilfanov.messenger.data.source.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import timur.gilfanov.messenger.data.source.local.database.converter.Converters
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao
import timur.gilfanov.messenger.data.source.local.database.dao.SyncMetadataDao
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncMetadataEntity

/**
 * Room database for the Messenger application.
 * Manages all local data storage for chats, messages, participants, and sync metadata.
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ParticipantEntity::class,
        ChatParticipantCrossRef::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MessengerDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun participantDao(): ParticipantDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        const val DATABASE_NAME = "messenger_database"
    }
}

