package com.luc4n3x.levyra.data.yandex

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal class YandexMusicSessionStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _accountFlow = MutableStateFlow(loadAccount())
    val accountFlow: StateFlow<YandexAccount?> = _accountFlow.asStateFlow()

    fun loadToken(): String? {
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        return decrypt(encrypted)
    }

    fun saveToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_ACCOUNT).apply()
            _accountFlow.value = null
        } else {
            val encrypted = encrypt(token)
            prefs.edit().putString(KEY_TOKEN, encrypted).apply()
        }
    }

    fun loadAccount(): YandexAccount? {
        val jsonStr = prefs.getString(KEY_ACCOUNT, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            YandexAccount(
                uid = json.optString("uid"),
                login = json.optString("login"),
                displayName = json.optString("displayName"),
                avatarUrl = json.optString("avatarUrl"),
                hasPlus = json.optBoolean("hasPlus")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveAccount(account: YandexAccount?) {
        if (account == null) {
            prefs.edit().remove(KEY_ACCOUNT).apply()
            _accountFlow.value = null
        } else {
            val json = JSONObject().apply {
                put("uid", account.uid)
                put("login", account.login)
                put("displayName", account.displayName)
                put("avatarUrl", account.avatarUrl)
                put("hasPlus", account.hasPlus)
            }
            prefs.edit().putString(KEY_ACCOUNT, json.toString()).apply()
            _accountFlow.value = account
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        _accountFlow.value = null
    }

    private fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback to obfuscated base64 if hardware keystore fails
            "plain:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            if (encryptedBase64.startsWith("plain:")) {
                val decoded = Base64.decode(encryptedBase64.removePrefix("plain:"), Base64.NO_WRAP)
                return String(decoded, Charsets.UTF_8)
            }
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = combined.copyOfRange(0, 12)
            val cipherText = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: run {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    companion object {
        private const val PREFS_NAME = "levyra_yandex_music_session"
        private const val KEY_TOKEN = "yandex_token"
        private const val KEY_ACCOUNT = "yandex_account"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "levyra.yandex.music.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val KEY_LOCK = Any()

        @Volatile
        private var instance: YandexMusicSessionStore? = null

        fun getInstance(context: Context): YandexMusicSessionStore =
            instance ?: synchronized(this) {
                instance ?: YandexMusicSessionStore(context).also { instance = it }
            }
    }
}
