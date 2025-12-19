package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.user.repository.LocaleRepository
import timur.gilfanov.messenger.util.Logger

class ObserveAndApplyLocaleUseCase(
    private val observeUiLanguage: ObserveUiLanguageUseCase,
    private val localeRepository: LocaleRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "ObserveAndApplyLocaleUseCase"
    }

    operator fun invoke(): Flow<Unit> = observeUiLanguage()
        .map { result ->
            result.fold(
                onSuccess = { language ->
                    logger.i(TAG, "Applying locale: $language")
                    localeRepository.applyLocale(language)
                },
                onFailure = { error ->
                    logger.w(TAG, "Failed to observe language: $error")
                },
            )
        }
}
