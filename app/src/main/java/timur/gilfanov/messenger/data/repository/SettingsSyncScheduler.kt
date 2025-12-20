package timur.gilfanov.messenger.data.repository

import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.SettingKey

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
