package timur.gilfanov.messenger.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides DataStore-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Extension property for Context to get DataStore instance.
     * Creates a singleton DataStore for sync preferences.
     */
    private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "sync_preferences",
    )

    @Provides
    @Singleton
    fun provideSyncDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.syncDataStore
}
