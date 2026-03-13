package timur.gilfanov.messenger.domain.entity.settings

import kotlin.time.Instant

/**
 * Event emitted when a settings synchronization conflict occurs.
 *
 * A conflict happens when both the local device and the server have modified
 * the same setting since the last successful sync.
 *
 * @property settingKey The setting that experienced the conflict
 * @property localValue The value that was stored locally before conflict resolution
 * @property serverValue The value received from the server
 * @property acceptedValue The final value chosen after conflict resolution (may be server value or validated fallback)
 * @property conflictedAt Timestamp when the conflict was detected
 */
data class SettingsConflictEvent(
    val settingKey: SettingKey,
    val localValue: String,
    val serverValue: String,
    val acceptedValue: String,
    val conflictedAt: Instant,
)
