package timur.gilfanov.messenger.test

import timur.gilfanov.messenger.data.repository.SettingsSyncScheduler
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.SettingKey

class SettingsSyncSchedulerStub : SettingsSyncScheduler {
    override fun scheduleSettingSync(userId: UserId, key: SettingKey) = Unit
    override fun schedulePeriodicSync() = Unit
}
