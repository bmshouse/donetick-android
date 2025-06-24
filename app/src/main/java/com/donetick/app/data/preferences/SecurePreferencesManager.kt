package com.donetick.app.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.donetick.app.data.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure storage of application preferences using EncryptedSharedPreferences
 */
@Singleton
class SecurePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE_NAME = "donetick_secure_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_LAST_VALIDATED = "last_validated"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _serverConfigFlow = MutableStateFlow(getServerConfig())
    val serverConfigFlow: Flow<ServerConfig> = _serverConfigFlow.asStateFlow()

    /**
     * Retrieves the current server configuration
     */
    fun getServerConfig(): ServerConfig {
        return ServerConfig(
            url = encryptedPrefs.getString(KEY_SERVER_URL, "") ?: "",
            isConfigured = encryptedPrefs.getBoolean(KEY_IS_CONFIGURED, false),
            lastValidated = encryptedPrefs.getLong(KEY_LAST_VALIDATED, 0L)
        )
    }

    /**
     * Saves server configuration securely
     */
    fun saveServerConfig(config: ServerConfig) {
        encryptedPrefs.edit().apply {
            putString(KEY_SERVER_URL, config.url)
            putBoolean(KEY_IS_CONFIGURED, config.isConfigured)
            putLong(KEY_LAST_VALIDATED, config.lastValidated)
            apply()
        }
        _serverConfigFlow.value = config
    }

    /**
     * Updates server URL and marks as configured
     */
    fun updateServerUrl(url: String) {
        val config = ServerConfig(
            url = url,
            isConfigured = true,
            lastValidated = System.currentTimeMillis()
        )
        saveServerConfig(config)
    }

    /**
     * Clears all server configuration (disconnect)
     */
    fun clearServerConfig() {
        encryptedPrefs.edit().clear().apply()
        _serverConfigFlow.value = ServerConfig.empty()
    }

    /**
     * Checks if server is configured
     */
    fun isServerConfigured(): Boolean {
        return encryptedPrefs.getBoolean(KEY_IS_CONFIGURED, false)
    }

    /**
     * Updates last validated timestamp
     */
    fun updateLastValidated() {
        val currentConfig = getServerConfig()
        saveServerConfig(currentConfig.copy(lastValidated = System.currentTimeMillis()))
    }
}
