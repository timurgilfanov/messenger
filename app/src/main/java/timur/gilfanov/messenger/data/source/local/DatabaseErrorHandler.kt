package timur.gilfanov.messenger.data.source.local

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteOutOfMemoryException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import timur.gilfanov.messenger.util.Logger

internal class DatabaseErrorHandler(private val logger: Logger) {

    companion object {
        private const val TAG = "DatabaseErrorHandler"
    }

    fun mapException(exception: SQLiteException): LocalDataSourceError {
        logger.e(TAG, "Database operation failed", exception)

        return when (exception) {
            is SQLiteDatabaseCorruptException -> {
                logger.e(TAG, "Database corrupted")
                LocalDataSourceError.StorageUnavailable
            }
            is SQLiteOutOfMemoryException -> {
                logger.e(TAG, "SQLite out of memory")
                LocalDataSourceError.StorageFull
            }

            // Permission and access issues
            is SQLiteAccessPermException -> {
                logger.e(TAG, "Database access permission denied")
                LocalDataSourceError.StorageUnavailable
            }
            is SQLiteReadOnlyDatabaseException -> {
                logger.w(TAG, "Database is read-only")
                LocalDataSourceError.StorageUnavailable
            }

            // Disk and storage issues
            is SQLiteFullException -> {
                logger.w(TAG, "Storage is full")
                LocalDataSourceError.StorageFull
            }
            is SQLiteDiskIOException -> {
                logger.e(TAG, "Disk I/O error")
                LocalDataSourceError.StorageUnavailable
            }

            // Concurrency issues
            is SQLiteDatabaseLockedException -> {
                logger.w(TAG, "Database locked - concurrent modification")
                LocalDataSourceError.ConcurrentModificationError
            }

            // Constraint violations
            is SQLiteConstraintException -> mapConstraintError(exception)

            // Other SQLite errors
            else -> mapGeneralSQLiteError(exception)
        }
    }

    private fun mapConstraintError(e: SQLiteConstraintException): LocalDataSourceError {
        val message = e.message ?: ""
        logger.d(TAG, "Constraint violation: $message")

        return when {
            // UNIQUE constraint violations
            message.contains("UNIQUE constraint failed: chats.id") -> {
                val id = extractIdFromMessage(message, "chats.id")
                LocalDataSourceError.DuplicateEntity("chat", id)
            }
            message.contains("UNIQUE constraint failed: messages.id") -> {
                val id = extractIdFromMessage(message, "messages.id")
                LocalDataSourceError.DuplicateEntity("message", id)
            }
            message.contains("UNIQUE constraint failed: participants.id") -> {
                val id = extractIdFromMessage(message, "participants.id")
                LocalDataSourceError.DuplicateEntity("participant", id)
            }

            // Foreign key violations
            message.contains("FOREIGN KEY constraint failed") -> {
                when {
                    message.contains("chat_participants.participantId") -> {
                        val id = extractIdFromMessage(message, "participantId")
                        LocalDataSourceError.RelatedEntityMissing("participant", id)
                    }
                    message.contains("chat_participants.chatId") -> {
                        val id = extractIdFromMessage(message, "chatId")
                        LocalDataSourceError.RelatedEntityMissing("chat", id)
                    }
                    message.contains("messages.chatId") -> {
                        val id = extractIdFromMessage(message, "chatId")
                        LocalDataSourceError.RelatedEntityMissing("chat", id)
                    }
                    message.contains("messages.senderId") -> {
                        val id = extractIdFromMessage(message, "senderId")
                        LocalDataSourceError.RelatedEntityMissing("participant", id)
                    }
                    else -> {
                        logger.w(TAG, "Unmapped foreign key violation: $message")
                        LocalDataSourceError.UnknownError(e)
                    }
                }
            }

            else -> {
                logger.w(TAG, "Unmapped constraint error: $message")
                LocalDataSourceError.UnknownError(e)
            }
        }
    }

    private fun mapGeneralSQLiteError(e: SQLiteException): LocalDataSourceError {
        val message = e.message ?: ""
        return when {
            message.contains("database is locked") -> {
                logger.w(TAG, "Database locked")
                LocalDataSourceError.ConcurrentModificationError
            }
            message.contains("no such table") || message.contains("no such column") -> {
                logger.e(TAG, "Database schema error: $message")
                LocalDataSourceError.StorageUnavailable
            }
            message.contains("database disk image is malformed") -> {
                logger.e(TAG, "Database corrupted: $message")
                LocalDataSourceError.StorageUnavailable
            }
            else -> {
                logger.w(TAG, "Unmapped SQLite error: $message")
                LocalDataSourceError.UnknownError(e)
            }
        }
    }

    private fun extractIdFromMessage(message: String, context: String): String {
        // Try to find UUID pattern
        val uuidRegex =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".toRegex()

        return uuidRegex.find(message)?.value ?: run {
            // Fallback: try to extract last word/token that might be an ID
            val parts = message.split("=", ":", " ", "'", "\"")
            parts.lastOrNull { it.isNotBlank() && it != context } ?: "unknown"
        }
    }
}
