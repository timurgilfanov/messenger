package timur.gilfanov.messenger.test

import timur.gilfanov.messenger.data.repository.SettingsSyncScheduler
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.settings.SettingKey

class SettingsSyncSchedulerStub : SettingsSyncScheduler {
    override fun scheduleSettingSync(userKey: UserScopeKey, key: SettingKey) = Unit
    override fun schedulePeriodicSync() = Unit
    override suspend fun cancelUserScopedJobs(userKey: UserScopeKey) = Unit
}
