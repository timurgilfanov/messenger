package timur.gilfanov.messenger.auth.ui.utils

import android.content.Context
import kotlin.time.Duration
import timur.gilfanov.messenger.auth.R

private const val SECONDS_PER_MINUTE = 60

internal fun tooManyAttemptsDisplayString(context: Context, remaining: Duration): String {
    val totalSeconds = remaining.inWholeSeconds
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val formatted = when {
        minutes > 0 && seconds > 0 ->
            "${
                context.resources.getQuantityString(
                    R.plurals.cooldown_minutes,
                    minutes.toInt(),
                    minutes,
                )
            } " +
                context.resources.getQuantityString(
                    R.plurals.cooldown_seconds,
                    seconds.toInt(),
                    seconds,
                )

        minutes > 0 ->
            context.resources.getQuantityString(
                R.plurals.cooldown_minutes,
                minutes.toInt(),
                minutes,
            )

        else ->
            context.resources.getQuantityString(
                R.plurals.cooldown_seconds,
                seconds.toInt(),
                seconds,
            )
    }
    return context.getString(R.string.snackbar_error_too_many_attempts, formatted)
}
