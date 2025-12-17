package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.LocaleRepository

class LocaleRepositoryStub(private var currentLocale: UiLanguage? = null) : LocaleRepository {

    val appliedLocales = mutableListOf<UiLanguage>()

    override fun applyLocale(language: UiLanguage) {
        appliedLocales.add(language)
        currentLocale = language
    }

    override fun getCurrentLocale(): UiLanguage? = currentLocale
}
