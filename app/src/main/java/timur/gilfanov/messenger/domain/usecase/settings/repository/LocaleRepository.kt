package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

interface LocaleRepository {
    suspend fun applyLocale(language: UiLanguage)
}
