package com.donetick.app.di

import com.donetick.app.data.repository.ServerRepository
import com.donetick.app.domain.usecase.CheckServerConnectivityUseCase
import com.donetick.app.domain.usecase.GetServerConfigUseCase
import com.donetick.app.domain.usecase.ManageServerConfigUseCase
import com.donetick.app.domain.usecase.ValidateServerUrlUseCase
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
