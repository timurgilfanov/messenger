package timur.gilfanov.messenger.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCaseImpl

@Module
@InstallIn(ViewModelComponent::class)
object ProfileModule {

    @Provides
    fun provideObserveProfileUseCase(): ObserveProfileUseCase = ObserveProfileUseCaseImpl()
}
