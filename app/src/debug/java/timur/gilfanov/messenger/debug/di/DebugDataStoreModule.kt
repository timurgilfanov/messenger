package timur.gilfanov.messenger.debug.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Hilt module for debug-only dependencies.
 * This module provides DataStore for debug preferences and coroutine scope for background operations.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugDataStoreModule {

    /**
     * Extension property for Context to get debug DataStore instance.
     * Creates a singleton DataStore for debug preferences.
     */
    private val Context.debugDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "debug_preferences",
    )

    @Provides
    @Singleton
    @Named("debug")
    fun provideDebugDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.debugDataStore

    /**
     * Provide a coroutine scope for debug repository background operations.
     * Uses SupervisorJob so individual coroutine failures don't cancel the whole scope.
     */
    @Provides
    @Singleton
    @Named("debug")
    fun provideDebugCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
