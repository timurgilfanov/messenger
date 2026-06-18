package timur.gilfanov.messenger.data.source.local.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import timur.gilfanov.messenger.data.source.local.database.converter.Converters
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao
import timur.gilfanov.messenger.data.source.local.database.dao.SettingsDao
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.profile.data.source.local.ProfileDao
import timur.gilfanov.messenger.profile.data.source.local.ProfileEntity

/**
 * Room database for the Messenger application.
 * Manages all local data storage for chats, messages, and participants.
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ParticipantEntity::class,
        ChatParticipantCrossRef::class,
        SettingEntity::class,
        ProfileEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 3, to = 4, spec = MessengerDatabase.Migration3To4::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = MessengerDatabase.Migration5To6::class),
    ],
)
@TypeConverters(Converters::class)
abstract class MessengerDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun participantDao(): ParticipantDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao

    @RenameColumn(tableName = "settings", fromColumnName = "userId", toColumnName = "userKey")
    class Migration3To4 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // Clear settings because the userKey derivation changed (plain userId → hashed token);
            // existing rows would never match any valid key and are effectively orphaned.
            db.execSQL("DELETE FROM settings")
        }
    }

    /**
     * Clears the settings table after the `userKey` derivation changed from
     * `sha256(refreshToken)` to `sha256(sessionId)`. Existing rows are keyed by the old
     * derivation and would never match a valid key, so they are dropped as orphaned data.
     */
    class Migration5To6 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM settings")
        }
    }

    companion object {
        const val DATABASE_NAME = "messenger_database"
    }
}
