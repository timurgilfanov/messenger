package timur.gilfanov.messenger.data.source.remote

import androidx.annotation.IntRange

/**
 * Represents the result of parsing a setting from the remote server.
 *
 * Server Version Contract:
 * - Server MUST use versions starting from 1
 * - Version 0 is reserved for client-side "unknown/never synced" state
 * - [Valid] and [InvalidValue] enforce serverVersion >= 1
 *
 * @see timur.gilfanov.messenger.data.source.local.LocalSetting for version semantics
 */
sealed interface RemoteSetting<out T> {
    data class Valid<T>(val value: T, @param:IntRange(from = 1) val serverVersion: Int) :
        RemoteSetting<T> {
        init {
            require(serverVersion >= 1) {
                "serverVersion must be >= 1 (got $serverVersion). Server violated version contract."
            }
        }
    }

    data object Missing : RemoteSetting<Nothing>

    data class InvalidValue<T>(
        val rawValue: String,
        @param:IntRange(from = 1) val serverVersion: Int,
    ) : RemoteSetting<T> {
        init {
            require(serverVersion >= 1) {
                "serverVersion must be >= 1 (got $serverVersion). Server violated version contract."
            }
        }
    }
}
