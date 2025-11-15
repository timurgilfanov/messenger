package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository

class SettingsRepositoryStub(
    private val settingsFlow: Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        emptyFlow(),
    private val changeLanguage: ResultWithError<Unit, ChangeLanguageRepositoryError> =
        ResultWithError.Success(Unit),
) : SettingsRepository {

    constructor(
        settings: ResultWithError<Settings, GetSettingsRepositoryError>,
        changeLanguageResult: ResultWithError<Unit, ChangeLanguageRepositoryError> =
            ResultWithError.Success(Unit),
    ) : this(
        settingsFlow = flowOf(settings),
        changeLanguage = changeLanguageResult,
    )

    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> = settingsFlow

    override fun observeConflicts(): Flow<SettingsConflictEvent> {
        error("Not implemented for this test")
    }

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = changeLanguage
}
