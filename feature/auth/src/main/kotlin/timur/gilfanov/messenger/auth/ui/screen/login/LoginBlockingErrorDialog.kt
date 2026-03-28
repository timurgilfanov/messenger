package timur.gilfanov.messenger.auth.ui.screen.login

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import timur.gilfanov.messenger.auth.R
import timur.gilfanov.messenger.ui.theme.MessengerTheme

private data class BlockingErrorResources(
    val titleRes: Int,
    val messageRes: Int,
    val actionLabelRes: Int,
)

private fun LoginBlockingError.toResources(): BlockingErrorResources = when (this) {
    LoginBlockingError.StorageFull -> BlockingErrorResources(
        R.string.auth_error_storage_full_title,
        R.string.auth_error_storage_full,
        R.string.auth_action_open_storage_settings,
    )

    LoginBlockingError.StorageCorrupted -> BlockingErrorResources(
        R.string.auth_error_storage_corrupted_title,
        R.string.auth_error_storage_corrupted,
        R.string.auth_action_open_app_settings,
    )

    LoginBlockingError.StorageReadOnly -> BlockingErrorResources(
        R.string.auth_error_storage_read_only_title,
        R.string.auth_error_storage_read_only,
        R.string.auth_action_open_app_settings,
    )

    LoginBlockingError.StorageAccessDenied -> BlockingErrorResources(
        R.string.auth_error_storage_access_denied_title,
        R.string.auth_error_storage_access_denied,
        R.string.auth_action_open_app_settings,
    )
}

@Composable
internal fun LoginBlockingErrorDialog(
    error: LoginBlockingError,
    onOpenAppSettings: () -> Unit,
    onOpenStorageSettings: () -> Unit,
) {
    val res = error.toResources()
    val onClick = when (error) {
        LoginBlockingError.StorageFull -> onOpenStorageSettings

        LoginBlockingError.StorageCorrupted,
        LoginBlockingError.StorageReadOnly,
        LoginBlockingError.StorageAccessDenied,
        -> onOpenAppSettings
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(res.titleRes)) },
        text = { Text(stringResource(res.messageRes)) },
        confirmButton = {
            TextButton(
                onClick = onClick,
                modifier = Modifier.testTag("login_blocking_error_action_button"),
            ) { Text(stringResource(res.actionLabelRes)) }
        },
        modifier = Modifier.testTag("login_blocking_error_dialog"),
    )
}

@Preview
@Composable
private fun LoginBlockingErrorDialogStorageFullPreview() {
    MessengerTheme {
        LoginBlockingErrorDialog(
            error = LoginBlockingError.StorageFull,
            onOpenAppSettings = {},
            onOpenStorageSettings = {},
        )
    }
}

@Preview
@Composable
private fun LoginBlockingErrorDialogCorruptedPreview() {
    MessengerTheme {
        LoginBlockingErrorDialog(
            error = LoginBlockingError.StorageCorrupted,
            onOpenAppSettings = {},
            onOpenStorageSettings = {},
        )
    }
}

@Preview
@Composable
private fun LoginBlockingErrorDialogReadOnlyPreview() {
    MessengerTheme {
        LoginBlockingErrorDialog(
            error = LoginBlockingError.StorageReadOnly,
            onOpenAppSettings = {},
            onOpenStorageSettings = {},
        )
    }
}

@Preview
@Composable
private fun LoginBlockingErrorDialogAccessDeniedPreview() {
    MessengerTheme {
        LoginBlockingErrorDialog(
            error = LoginBlockingError.StorageAccessDenied,
            onOpenAppSettings = {},
            onOpenStorageSettings = {},
        )
    }
}
