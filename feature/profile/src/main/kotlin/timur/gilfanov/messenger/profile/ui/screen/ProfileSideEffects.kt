package timur.gilfanov.messenger.profile.ui.screen

import timur.gilfanov.messenger.domain.usecase.profile.repository.ObserveProfileRepositoryError

sealed interface ProfileSideEffects {
    data class ObserveProfileFailed(val error: ObserveProfileRepositoryError) : ProfileSideEffects
}
