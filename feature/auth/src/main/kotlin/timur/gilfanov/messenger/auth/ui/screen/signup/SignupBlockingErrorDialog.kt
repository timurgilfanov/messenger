package timur.gilfanov.messenger.auth.ui.screen.signup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import timur.gilfanov.messenger.auth.R

private data class SignupBlockingErrorResources(
    val titleRes: Int,
    val messageRes: Int,
    val actionLabelRes: Int,
)

private fun SignupBlockingError.toResources(): SignupBlockingErrorResources = when (this) {
    SignupBlockingError.StorageFull -> SignupBlockingErrorResources(
        R.string.dialog_error_storage_full_title,
        R.string.dialog_error_storage_full,
        R.string.dialog_action_open_storage_settings,
    )

    SignupBlockingError.StorageCorrupted -> SignupBlockingErrorResources(
        R.string.dialog_error_storage_corrupted_title,
        R.string.dialog_error_storage_corrupted,
        R.string.dialog_action_open_app_settings,
    )

    SignupBlockingError.StorageReadOnly -> SignupBlockingErrorResources(
        R.string.dialog_error_storage_read_only_title,
        R.string.dialog_error_storage_read_only,
        R.string.dialog_action_open_app_settings,
    )

    SignupBlockingError.StorageAccessDenied -> SignupBlockingErrorResources(
        R.string.dialog_error_storage_access_denied_title,
        R.string.dialog_error_storage_access_denied,
        R.string.dialog_action_open_app_settings,
    )
}

@Composable
internal fun SignupBlockingErrorDialog(
    error: SignupBlockingError,
    onOpenAppSettings: () -> Unit,
    onOpenStorageSettings: () -> Unit,
) {
    val res = error.toResources()
    val onClick = when (error) {
        SignupBlockingError.StorageFull -> onOpenStorageSettings

        SignupBlockingError.StorageCorrupted,
        SignupBlockingError.StorageReadOnly,
        SignupBlockingError.StorageAccessDenied,
        -> onOpenAppSettings
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(res.titleRes)) },
        text = { Text(stringResource(res.messageRes)) },
        confirmButton = {
            TextButton(
                onClick = onClick,
                modifier = Modifier.testTag("signup_blocking_error_action_button"),
            ) { Text(stringResource(res.actionLabelRes)) }
        },
        modifier = Modifier.testTag("signup_blocking_error_dialog"),
    )
}
