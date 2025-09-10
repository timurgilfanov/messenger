package timur.gilfanov.messenger.debug

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
import timur.gilfanov.messenger.util.Logger

/**
 * Hilt EntryPoint for accessing dependencies in DebugMessengerApplication.
 * This is needed because Application classes don't support @Inject field injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugApplicationEntryPoint {
    fun logger(): Logger
    fun debugDataRepository(): DebugDataRepository
    fun debugNotificationService(): DebugNotificationService
    fun localDebugDataSource(): LocalDebugDataSource
}
