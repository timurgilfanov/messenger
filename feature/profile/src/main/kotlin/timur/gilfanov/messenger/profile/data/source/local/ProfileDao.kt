package timur.gilfanov.messenger.profile.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the user-scoped profile cache stored in the `profiles` table.
 */
@Dao
interface ProfileDao {
    /**
     * Returns a cold [Flow] emitting the cached profile for [userKey], or `null` when no row
     * exists. Re-emits whenever the row is inserted, updated, or deleted.
     */
    @Query("SELECT * FROM profiles WHERE userKey = :userKey")
    fun observeProfile(userKey: String): Flow<ProfileEntity?>

    /**
     * Inserts [profile] or replaces the existing row with the same primary key.
     */
    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    /**
     * Deletes the cached profile row owned by [userKey]; no-op when none exists.
     */
    @Query("DELETE FROM profiles WHERE userKey = :userKey")
    suspend fun deleteByUserKey(userKey: String)
}
