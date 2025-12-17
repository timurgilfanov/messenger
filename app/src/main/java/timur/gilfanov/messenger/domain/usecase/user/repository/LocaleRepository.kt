package timur.gilfanov.messenger.domain.usecase.user.repository

import timur.gilfanov.messenger.domain.entity.user.UiLanguage

interface LocaleRepository {
    fun applyLocale(language: UiLanguage)
    fun getCurrentLocale(): UiLanguage?
}
