package timur.gilfanov.messenger.ui.screen.settings

import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError

sealed interface ProfileSideEffects {
    data object Unauthorized : ProfileSideEffects
    data class ObserveProfileFailed(val error: ObserveProfileRepositoryError) : ProfileSideEffects
}
