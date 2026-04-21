package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.Flow

fun interface ObserveAndApplyLocaleUseCase {
    operator fun invoke(): Flow<Unit>
}
