package timur.gilfanov.messenger.ui.screen.user

import timur.gilfanov.messenger.domain.usecase.user.repository.ObserveProfileRepositoryError

sealed interface ProfileSideEffects {
    data object Unauthorized : ProfileSideEffects
    data class GetProfileFailed(val error: ObserveProfileRepositoryError) : ProfileSideEffects
}
