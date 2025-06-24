package com.donetick.app.di

import android.content.Context
import com.donetick.app.data.preferences.SecurePreferencesManager
import com.donetick.app.data.repository.ServerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing application-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSecurePreferencesManager(
        @ApplicationContext context: Context
    ): SecurePreferencesManager {
        return SecurePreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideServerRepository(
        preferencesManager: SecurePreferencesManager
    ): ServerRepository {
        return ServerRepository(preferencesManager)
    }
}
