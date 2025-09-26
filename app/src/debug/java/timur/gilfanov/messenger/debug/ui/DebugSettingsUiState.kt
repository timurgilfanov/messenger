package timur.gilfanov.messenger.debug.ui

import timur.gilfanov.messenger.debug.DebugSettings

data class DebugSettingsUiState(
    val settings: DebugSettings = DebugSettings(),
    val isLoading: Boolean = false,
)
