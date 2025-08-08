package timur.gilfanov.messenger.data.source.local.database.converter

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.data.source.local.database.entity.MessageType
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus

/**
 * Type converters for Room database.
 * Handles conversion between custom types and types that Room can persist.
 */
class Converters {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(milliseconds: Long?): Instant? =
        milliseconds?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun fromMessageType(type: MessageType): String = type.name

    @TypeConverter
    fun toMessageType(type: String): MessageType = MessageType.valueOf(type)

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(status: String): SyncStatus = SyncStatus.valueOf(status)
}
