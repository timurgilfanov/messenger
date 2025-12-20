package timur.gilfanov.messenger.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.LocaleRepository
import timur.gilfanov.messenger.util.Logger

@Singleton
class LocaleRepositoryImpl @Inject constructor(private val logger: Logger) : LocaleRepository {
    companion object {
        private const val TAG = "LocaleRepository"
    }

    override suspend fun applyLocale(language: UiLanguage) {
        val newLocaleList = language.toLocaleListCompat()
        val currentLocaleList = AppCompatDelegate.getApplicationLocales()
        if (currentLocaleList != newLocaleList) {
            // Triggers activity recreate() and must be called from the main thread
            withContext(Dispatchers.Main.immediate) {
                AppCompatDelegate.setApplicationLocales(newLocaleList)
            }
            logger.i(TAG, "Locale changed to $language")
        }
    }
}

private fun UiLanguage.toLocaleListCompat(): LocaleListCompat = LocaleListCompat.create(
    when (this) {
        UiLanguage.English -> Locale.ENGLISH
        UiLanguage.German -> Locale.GERMAN
    },
)
