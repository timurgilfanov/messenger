package timur.gilfanov.messenger.profile.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE userKey = :userKey")
    fun observeProfile(userKey: String): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE userKey = :userKey")
    suspend fun deleteByUserKey(userKey: String)
}
