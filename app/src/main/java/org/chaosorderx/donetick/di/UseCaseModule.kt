package org.chaosorderx.donetick.di

import org.chaosorderx.donetick.data.repository.ServerRepository
import org.chaosorderx.donetick.domain.usecase.CheckServerConnectivityUseCase
import org.chaosorderx.donetick.domain.usecase.GetServerConfigUseCase
import org.chaosorderx.donetick.domain.usecase.ManageServerConfigUseCase
import org.chaosorderx.donetick.domain.usecase.ValidateServerUrlUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing use case dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideValidateServerUrlUseCase(
        serverRepository: ServerRepository
    ): ValidateServerUrlUseCase {
        return ValidateServerUrlUseCase(serverRepository)
    }

    @Provides
    @Singleton
    fun provideGetServerConfigUseCase(
        serverRepository: ServerRepository
    ): GetServerConfigUseCase {
        return GetServerConfigUseCase(serverRepository)
    }

    @Provides
    @Singleton
    fun provideCheckServerConnectivityUseCase(
        serverRepository: ServerRepository
    ): CheckServerConnectivityUseCase {
        return CheckServerConnectivityUseCase(serverRepository)
    }

    @Provides
    @Singleton
    fun provideManageServerConfigUseCase(
        serverRepository: ServerRepository
    ): ManageServerConfigUseCase {
        return ManageServerConfigUseCase(serverRepository)
    }
}
