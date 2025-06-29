package com.donetick.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.donetick.app.data.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64
import java.security.SecureRandom

/**
 * Manages secure storage of application preferences using Android Keystore
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
        private const val MASTER_KEY_ALIAS = "donetick_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    init {
        generateOrGetMasterKey()
    }

    private val _serverConfigFlow = MutableStateFlow(getServerConfig())
    val serverConfigFlow: Flow<ServerConfig> = _serverConfigFlow.asStateFlow()

    private fun generateOrGetMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        return if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun encrypt(plaintext: String): String {
        val secretKey = generateOrGetMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val encryptedData = cipher.doFinal(plaintext.toByteArray())

        // Combine IV and encrypted data
        val combined = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)

        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun decrypt(encryptedData: String): String {
        val secretKey = generateOrGetMasterKey()
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)

        // Extract IV and encrypted data
        val iv = ByteArray(GCM_IV_LENGTH)
        val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

        val decryptedData = cipher.doFinal(encrypted)
        return String(decryptedData)
    }

    /**
     * Retrieves the current server configuration
     */
    fun getServerConfig(): ServerConfig {
        val encryptedUrl = sharedPreferences.getString(KEY_SERVER_URL, "") ?: ""
        val url = if (encryptedUrl.isNotEmpty()) {
            try {
                decrypt(encryptedUrl)
            } catch (e: Exception) {
                "" // Return empty string if decryption fails
            }
        } else {
            ""
        }

        return ServerConfig(
            url = url,
            isConfigured = sharedPreferences.getBoolean(KEY_IS_CONFIGURED, false),
            lastValidated = sharedPreferences.getLong(KEY_LAST_VALIDATED, 0L)
        )
    }

    /**
     * Saves server configuration securely
     */
    fun saveServerConfig(config: ServerConfig) {
        sharedPreferences.edit().apply {
            val encryptedUrl = if (config.url.isNotEmpty()) encrypt(config.url) else ""
            putString(KEY_SERVER_URL, encryptedUrl)
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
        sharedPreferences.edit().clear().apply()
        _serverConfigFlow.value = ServerConfig.empty()
    }

    /**
     * Checks if server is configured
     */
    fun isServerConfigured(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_CONFIGURED, false)
    }

    /**
     * Updates last validated timestamp
     */
    fun updateLastValidated() {
        val currentConfig = getServerConfig()
        saveServerConfig(currentConfig.copy(lastValidated = System.currentTimeMillis()))
    }
}
