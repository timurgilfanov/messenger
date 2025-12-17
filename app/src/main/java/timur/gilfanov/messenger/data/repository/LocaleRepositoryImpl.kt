package timur.gilfanov.messenger.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import javax.inject.Inject
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.LocaleRepository
import timur.gilfanov.messenger.util.Logger

class LocaleRepositoryImpl @Inject constructor(private val logger: Logger) : LocaleRepository {
    companion object {
        private const val TAG = "LocaleRepository"
    }

    override fun applyLocale(language: UiLanguage) {
        val newLocaleList = language.toLocaleListCompat()
        val currentLocaleList = AppCompatDelegate.getApplicationLocales()
        if (currentLocaleList != newLocaleList) {
            AppCompatDelegate.setApplicationLocales(newLocaleList)
            logger.i(TAG, "Locale changed to $language via AppCompatDelegate")
        }
    }

    override fun getCurrentLocale(): UiLanguage? =
        AppCompatDelegate.getApplicationLocales().toUiLanguageOrNull().also {
            logger.i(TAG, "Current locale is $it (from AppCompatDelegate)")
        }
}

private fun UiLanguage.toLocaleListCompat(): LocaleListCompat = LocaleListCompat.create(
    when (this) {
        UiLanguage.English -> Locale.ENGLISH
        UiLanguage.German -> Locale.GERMAN
    },
)

private fun LocaleListCompat.toUiLanguageOrNull(): UiLanguage? = when {
    isEmpty -> null

    else -> when (get(0)?.language) {
        "en" -> UiLanguage.English
        "de" -> UiLanguage.German
        else -> null
    }
}
