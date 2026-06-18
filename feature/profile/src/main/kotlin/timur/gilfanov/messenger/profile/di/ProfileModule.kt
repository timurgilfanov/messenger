package timur.gilfanov.messenger.profile.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.UserScopedDataCleaner
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.domain.usecase.profile.repository.ProfileRepository
import timur.gilfanov.messenger.profile.data.repository.ProfileRepositoryImpl
import timur.gilfanov.messenger.profile.data.repository.ProfileUserScopedDataCleaner
import timur.gilfanov.messenger.profile.data.source.local.LocalProfileDataSource
import timur.gilfanov.messenger.profile.data.source.local.LocalProfileDataSourceImpl
import timur.gilfanov.messenger.profile.data.source.remote.RemoteProfileDataSource
import timur.gilfanov.messenger.profile.data.source.remote.RemoteProfileDataSourceImpl
import timur.gilfanov.messenger.profile.domain.usecase.ObserveProfileUseCaseImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileDataModule {

    @Binds
    @Singleton
    abstract fun bindLocalProfileDataSource(
        impl: LocalProfileDataSourceImpl,
    ): LocalProfileDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteProfileDataSource(
        impl: RemoteProfileDataSourceImpl,
    ): RemoteProfileDataSource

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @IntoSet
    abstract fun bindProfileUserScopedDataCleaner(
        impl: ProfileUserScopedDataCleaner,
    ): UserScopedDataCleaner
}

@Module
@InstallIn(ViewModelComponent::class)
object ProfileModule {

    @Provides
    fun provideObserveProfileUseCase(
        authRepository: AuthRepository,
        profileRepository: ProfileRepository,
    ): ObserveProfileUseCase = ObserveProfileUseCaseImpl(authRepository, profileRepository)
}
