package timur.gilfanov.messenger.ui.screen.settings

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

/**
 * UI model for user settings display.
 *
 * Represents user preferences in a UI-friendly format for display in
 * the settings screen.
 *
 * @property language Currently selected UI language
 */
@Immutable
@Parcelize
data class SettingsUi(
    @TypeParceler<UiLanguage, UiLanguageParceler>()
    val language: UiLanguage,
) : Parcelable

object UiLanguageParceler : Parceler<UiLanguage> {
    override fun create(parcel: Parcel): UiLanguage = when (parcel.readInt()) {
        0 -> UiLanguage.English
        1 -> UiLanguage.German
        else -> UiLanguage.English
    }

    override fun UiLanguage.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(
            when (this) {
                UiLanguage.English -> 0
                UiLanguage.German -> 1
            },
        )
    }
}

fun Settings.toSettingsUi(): SettingsUi = SettingsUi(language = uiLanguage)
