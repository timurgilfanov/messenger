package timur.gilfanov.messenger.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import timur.gilfanov.messenger.auth.domain.usecase.LogoutUseCase
import timur.gilfanov.messenger.auth.domain.usecase.LogoutUseCaseImpl
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

@Module
@InstallIn(ViewModelComponent::class)
object LogoutModule {

    @Provides
    fun provideLogoutUseCase(authRepository: AuthRepository, logger: Logger): LogoutUseCase =
        LogoutUseCaseImpl(authRepository, logger)
}
