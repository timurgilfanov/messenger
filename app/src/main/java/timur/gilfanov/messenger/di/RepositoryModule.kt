package timur.gilfanov.messenger.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timur.gilfanov.messenger.data.repository.InMemoryParticipantRepository
import timur.gilfanov.messenger.data.repository.InMemoryPrivilegedRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.privileged.PrivilegedRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindParticipantRepository(
        inMemoryParticipantRepository: InMemoryParticipantRepository,
    ): ParticipantRepository

    @Binds
    abstract fun bindPrivilegedRepository(
        inMemoryPrivilegedRepository: InMemoryPrivilegedRepository,
    ): PrivilegedRepository
}
