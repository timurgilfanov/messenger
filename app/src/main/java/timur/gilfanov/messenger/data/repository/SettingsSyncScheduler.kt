package timur.gilfanov.messenger.data.repository

import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Schedules background sync operations for settings.
 */
interface SettingsSyncScheduler {

    /**
     * Schedule a one-off sync for a particular setting.
     *
     * When a new job is scheduled for the same setting and user, it is appended to any
     * in-flight work to ensure events are processed sequentially.
     */
    fun scheduleSettingSync(userId: UserId, key: SettingKey)

    /**
     * Schedule periodic sync for all settings to keep local state in sync with server.
     */
    fun schedulePeriodicSync()
}
