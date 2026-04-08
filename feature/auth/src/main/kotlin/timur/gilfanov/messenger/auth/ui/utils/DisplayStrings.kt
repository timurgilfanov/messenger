package timur.gilfanov.messenger.auth.ui.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.time.Duration
import timur.gilfanov.messenger.auth.R
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError

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
    return context.getString(R.string.auth_error_too_many_attempts, formatted)
}

@Composable
internal fun PasswordValidationError.toDisplayString(): String = when (this) {
    is PasswordValidationError.PasswordTooShort -> when (val len = minLength) {
        null -> stringResource(R.string.auth_error_invalid_password_server)
        else -> stringResource(R.string.auth_error_password_too_short, len)
    }

    is PasswordValidationError.PasswordTooLong -> when (val len = maxLength) {
        null -> stringResource(R.string.auth_error_invalid_password_server)
        else -> stringResource(R.string.auth_error_password_too_long, len)
    }

    is PasswordValidationError.ForbiddenCharacterInPassword ->
        stringResource(R.string.auth_error_forbidden_character_in_password, character)

    is PasswordValidationError.PasswordMustContainNumbers ->
        pluralStringResource(
            R.plurals.auth_error_password_must_contain_numbers,
            minNumbers,
            minNumbers,
        )

    is PasswordValidationError.PasswordMustContainAlphabet ->
        pluralStringResource(
            R.plurals.auth_error_password_must_contain_alphabet,
            minAlphabet,
            minAlphabet,
        )

    is PasswordValidationError.UnknownRuleViolation ->
        stringResource(R.string.auth_error_invalid_password_server)
}
