package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository

class LocaleRepositoryStub(private var currentLocale: UiLanguage? = null) : LocaleRepository {

    val appliedLocales = mutableListOf<UiLanguage>()

    override suspend fun applyLocale(language: UiLanguage) {
        appliedLocales.add(language)
        currentLocale = language
    }
}
