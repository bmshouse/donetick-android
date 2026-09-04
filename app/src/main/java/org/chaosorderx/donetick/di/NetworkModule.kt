package org.chaosorderx.donetick.di

import org.chaosorderx.donetick.data.remote.HttpClient
import org.chaosorderx.donetick.data.remote.HttpSyncApi
import org.chaosorderx.donetick.data.remote.SyncApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the concrete [HttpSyncApi] to the [SyncApi] interface. Everything else in the
 * native sync path ([HttpClient], SyncStateStore, ChoreSyncCoordinator) is constructor-injected.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSyncApi(httpClient: HttpClient): SyncApi = HttpSyncApi(httpClient)
}
