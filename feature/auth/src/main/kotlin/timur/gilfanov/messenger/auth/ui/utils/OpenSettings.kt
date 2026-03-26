package timur.gilfanov.messenger.auth.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

internal fun openStorageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
    if (intent.resolveActivity(context.packageManager) == null) {
        intent.action = Settings.ACTION_SETTINGS
    }
    context.startActivity(intent)
}
