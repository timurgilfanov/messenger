package timur.gilfanov.messenger.test

import timur.gilfanov.messenger.data.repository.SettingsSyncScheduler
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId

class SettingsSyncSchedulerStub : SettingsSyncScheduler {
    override fun scheduleSettingSync(userId: UserId, key: SettingKey) = Unit
    override fun schedulePeriodicSync() = Unit
}
