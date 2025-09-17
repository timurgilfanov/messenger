package timur.gilfanov.messenger.testutil

import java.util.Locale
import java.util.TimeZone
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class LocaleTimeZoneRule(
    private val locale: Locale = Locale.US,
    private val timeZone: TimeZone = TimeZone.getTimeZone("UTC"),
) : TestWatcher() {

    private lateinit var previousLocale: Locale
    private lateinit var previousTimeZone: TimeZone

    override fun starting(description: Description) {
        previousLocale = Locale.getDefault()
        previousTimeZone = TimeZone.getDefault()
        Locale.setDefault(locale)
        TimeZone.setDefault(timeZone)
    }

    override fun finished(description: Description) {
        Locale.setDefault(previousLocale)
        TimeZone.setDefault(previousTimeZone)
    }
}
