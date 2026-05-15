package timur.gilfanov.messenger.profile.ui.screen

import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError

sealed interface ProfileSideEffects {
    data class ObserveProfileFailed(val error: ObserveProfileError) : ProfileSideEffects
}
