package timur.gilfanov.messenger.profile.data.source.local

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.util.Logger

@Singleton
class LocalProfileDataSourceImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val logger: Logger,
) : LocalProfileDataSource {
    companion object {
        private const val TAG = "LocalProfileDataSource"
    }

    override fun observeProfile(
        userKey: UserScopeKey,
    ): Flow<ResultWithError<Profile, ProfileLocalDataSourceError>> =
        profileDao.observeProfile(userKey.key)
            .map { entity ->
                if (entity == null) {
                    ResultWithError.Failure<Profile, ProfileLocalDataSourceError>(
                        ProfileLocalDataSourceError.UserDataNotFound,
                    )
                } else {
                    ResultWithError.Success<Profile, ProfileLocalDataSourceError>(
                        entity.toDomain(),
                    )
                }
            }
            .catch { throwable ->
                if (throwable is SQLiteException) {
                    emit(
                        ResultWithError.Failure<Profile, ProfileLocalDataSourceError>(
                            mapReadException(throwable),
                        ),
                    )
                } else {
                    throw throwable
                }
            }

    override suspend fun upsertProfile(
        userKey: UserScopeKey,
        profile: Profile,
    ): ResultWithError<Unit, ProfileLocalDataSourceError> = try {
        profileDao.upsert(profile.toEntity(userKey))
        ResultWithError.Success(Unit)
    } catch (e: SQLiteException) {
        ResultWithError.Failure(mapWriteException(e))
    }

    override suspend fun deleteProfile(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, ProfileLocalDataSourceError> = try {
        profileDao.deleteByUserKey(userKey.key)
        ResultWithError.Success(Unit)
    } catch (e: SQLiteException) {
        ResultWithError.Failure(mapWriteException(e))
    }

    private fun mapReadException(exception: SQLiteException): ProfileLocalDataSourceError {
        logger.e(TAG, "Failed to read profile", exception)
        return ProfileLocalDataSourceError.LocalDataSource(
            LocalDataSourceErrorV2.ReadError(exception.toErrorReason()),
        )
    }

    private fun mapWriteException(exception: SQLiteException): ProfileLocalDataSourceError {
        logger.e(TAG, "Failed to write profile", exception)
        return ProfileLocalDataSourceError.LocalDataSource(
            when (exception) {
                is SQLiteDatabaseCorruptException ->
                    LocalDataSourceErrorV2.SerializationError(exception.toErrorReason())

                is SQLiteAccessPermException,
                is SQLiteFullException,
                is SQLiteReadOnlyDatabaseException,
                is SQLiteDatabaseLockedException,
                is SQLiteDiskIOException,
                -> LocalDataSourceErrorV2.WriteError(exception.toErrorReason())

                else -> LocalDataSourceErrorV2.WriteError(exception.toErrorReason())
            },
        )
    }

    private fun SQLiteException.toErrorReason(): ErrorReason =
        ErrorReason(message ?: this::class.simpleName.orEmpty())
}
