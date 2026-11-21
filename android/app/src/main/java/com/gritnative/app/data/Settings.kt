package com.gritnative.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val deviceId: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val weekStartMonday: Boolean = true,
    val showCompleted: Boolean = true,
    val autoSync: Boolean = true,
    val alarmSound: Boolean = true,
    val lastSyncAt: String = "",
    val displayName: String = "",
    val displayNameUpdatedAt: Long = 0L,
    val publicTagIds: Set<String> = emptySet(),
    val publicTagsUpdatedAt: Long = 0L
)

enum class SyncStatus { OFF, IDLE, SYNCING, SYNCED, ERROR }

data class SyncUiState(
    val status: SyncStatus = SyncStatus.OFF,
    val message: String = "",
    val lastSyncAt: String = ""
)

/**
 * The sync key is encrypted with a non-exportable AES/GCM key held by the
 * Android keystore. It is never written to the store or to the APK.
 */
class SecurePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("grit_settings", Context.MODE_PRIVATE)

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    private fun key(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun getString(name: String, fallback: String = ""): String =
        prefs.getString(name, fallback) ?: fallback

    fun putString(name: String, value: String) {
        prefs.edit().putString(name, value).apply()
    }

    fun getBool(name: String, fallback: Boolean): Boolean = prefs.getBoolean(name, fallback)
    fun putBool(name: String, value: Boolean) = prefs.edit().putBoolean(name, value).apply()
    fun getInt(name: String, fallback: Int): Int = prefs.getInt(name, fallback)
    fun putInt(name: String, value: Int) = prefs.edit().putInt(name, value).apply()

    fun getStringSet(name: String): Set<String> =
        prefs.getStringSet(name, emptySet())?.toSet() ?: emptySet()

    fun putStringSet(name: String, value: Set<String>) {
        prefs.edit().putStringSet(name, value.toSet()).apply()
    }

    fun getSecret(name: String): String {
        val encoded = prefs.getString(name, null) ?: return ""
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size < 13) return ""
            val iv = bytes.copyOfRange(0, 12)
            val payload = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun putSecret(name: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(name).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + encrypted
            prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        } catch (_: Exception) {
            // Never fall back to plaintext. The user can enter the token again.
        }
    }

    companion object {
        private const val KEY_ALIAS = "grit_sync_secret_v1"
    }
}
