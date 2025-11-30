package timur.gilfanov.messenger.data.repository

import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId

interface SettingsSyncScheduler {

    /**
     * When new job scheduled for the setting that have on-going job, then new job will be appended
     * to the existing one.
     */
    fun scheduleSettingSync(userId: UserId, key: SettingKey)

    fun schedulePeriodicSync()
}
