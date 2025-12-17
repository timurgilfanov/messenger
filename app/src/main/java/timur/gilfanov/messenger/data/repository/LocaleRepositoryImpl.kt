package timur.gilfanov.messenger.data.repository

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.LocaleRepository
import timur.gilfanov.messenger.util.Logger

class LocaleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
) : LocaleRepository {
    companion object {
        private const val TAG = "LocaleRepository"
    }

    override fun applyLocale(language: UiLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val newLocales = LocaleList.forLanguageTags(language.toLanguageTag())
            val currentLocales = localeManager.applicationLocales
            if (currentLocales != newLocales) {
                localeManager.applicationLocales = newLocales
                logger.i(TAG, "Locale changed to $language via LocaleManager")
            }
        } else {
            val newLocaleList = language.toLocaleListCompat()
            val currentLocaleList = AppCompatDelegate.getApplicationLocales()
            if (currentLocaleList != newLocaleList) {
                AppCompatDelegate.setApplicationLocales(newLocaleList)
                logger.i(TAG, "Locale changed to $language via AppCompatDelegate")
            }
        }
    }

    override fun getCurrentLocale(): UiLanguage? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager.applicationLocales
            locales.toUiLanguageOrNull().also {
                logger.i(TAG, "Current locale is $it (from LocaleManager)")
            }
        } else {
            val localeList = AppCompatDelegate.getApplicationLocales()
            localeList.toUiLanguageOrNull().also {
                logger.i(TAG, "Current locale is $it (from AppCompatDelegate)")
            }
        }
}

private fun UiLanguage.toLanguageTag(): String = when (this) {
    UiLanguage.English -> "en"
    UiLanguage.German -> "de"
}

private fun UiLanguage.toLocaleListCompat(): LocaleListCompat = LocaleListCompat.create(
    when (this) {
        UiLanguage.English -> Locale.ENGLISH
        UiLanguage.German -> Locale.GERMAN
    },
)

private fun LocaleList.toUiLanguageOrNull(): UiLanguage? = when {
    isEmpty -> null

    else -> when (get(0)?.language) {
        "en" -> UiLanguage.English
        "de" -> UiLanguage.German
        else -> null
    }
}

private fun LocaleListCompat.toUiLanguageOrNull(): UiLanguage? = when {
    isEmpty -> null

    else -> when (get(0)?.language) {
        "en" -> UiLanguage.English
        "de" -> UiLanguage.German
        else -> null
    }
}
