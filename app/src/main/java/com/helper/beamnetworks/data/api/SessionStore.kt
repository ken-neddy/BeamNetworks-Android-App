package com.helper.beamnetworks.data.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class Session(
    val access: String,
    val refresh: String,
    val userId: Int,
    val email: String,
    val roleType: String,
    val permissions: List<String> = emptyList(),
    val mustChangePassword: Boolean = false,
    val tenantName: String? = null,
) {
    val isAdmin: Boolean get() = roleType == "tenant_admin" || "*" in permissions
}

/**
 * Holds the signed-in staff session. Tokens are encrypted with an AES-GCM key that lives in the
 * Android Keystore, then kept in private SharedPreferences.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("beam_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    val current: Session? get() = _session.value

    @Synchronized
    fun save(session: Session) {
        prefs.edit { putString(KEY, encrypt(json.encodeToString(Session.serializer(), session))) }
        _session.value = session
    }

    @Synchronized
    fun updateTokens(access: String, refresh: String?) {
        val existing = _session.value ?: return
        save(existing.copy(access = access, refresh = refresh ?: existing.refresh))
    }

    @Synchronized
    fun clear() {
        prefs.edit { remove(KEY) }
        _session.value = null
    }

    private fun load(): Session? = try {
        prefs.getString(KEY, null)?.let { json.decodeFromString(Session.serializer(), decrypt(it)) }
    } catch (e: Exception) {
        // The key was lost (app data restore, keystore reset): ask the user to sign in again
        prefs.edit { remove(KEY) }
        null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        }
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    private companion object {
        const val KEY = "session"
        const val KEY_ALIAS = "beam_session_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
