package timur.gilfanov.messenger.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.LocaleRepository
import timur.gilfanov.messenger.util.Logger

class LocaleRepositoryImpl @Inject constructor(private val logger: Logger) : LocaleRepository {
    companion object {
        private const val TAG = "LocaleRepository"
    }

    override suspend fun applyLocale(language: UiLanguage) {
        // AppCompatDelegate.setApplicationLocales triggers activity recreate() and must be called
        // from main thread. Application tests runs LaunchedEffect in test dispatcher and will be
        // failed in thread not switched to main.
        withContext(Dispatchers.Main.immediate) {
            val newLocaleList = language.toLocaleListCompat()
            val currentLocaleList = AppCompatDelegate.getApplicationLocales()
            if (currentLocaleList != newLocaleList) {
                AppCompatDelegate.setApplicationLocales(newLocaleList)
                logger.i(TAG, "Locale changed to $language via AppCompatDelegate")
            }
        }
    }
}

private fun UiLanguage.toLocaleListCompat(): LocaleListCompat = LocaleListCompat.create(
    when (this) {
        UiLanguage.English -> Locale.ENGLISH
        UiLanguage.German -> Locale.GERMAN
    },
)
