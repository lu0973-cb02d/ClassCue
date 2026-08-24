package com.classcue.kexing.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secret_store", Context.MODE_PRIVATE)

    @Synchronized
    fun getApiKey(profileId: String): String {
        val stored = decrypt(prefs.getString(storageKey(profileId), null)).orEmpty()
        if (stored.isNotBlank()) return stored
        if (profileId != DEFAULT_PROFILE_ID) return ""
        return decrypt(prefs.getString(LEGACY_KEY, null)).orEmpty()
    }

    @Synchronized
    fun setApiKey(profileId: String, value: String) {
        val key = storageKey(profileId)
        val editor = prefs.edit()
        if (value.isBlank()) editor.remove(key)
        else editor.putString(key, encrypt(value.trim()))
        if (profileId == DEFAULT_PROFILE_ID) editor.remove(LEGACY_KEY)
        editor.apply()
    }

    @Synchronized
    fun deleteApiKey(profileId: String) {
        val editor = prefs.edit().remove(storageKey(profileId))
        if (profileId == DEFAULT_PROFILE_ID) editor.remove(LEGACY_KEY)
        editor.apply()
    }

    fun hasApiKey(profileId: String): Boolean = getApiKey(profileId).isNotBlank()

    private fun storageKey(profileId: String): String =
        "api_key_profile_${profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")}"

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String?): String? = runCatching {
        if (value.isNullOrBlank()) return@runCatching null
        val combined = Base64.decode(value, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val payload = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(payload).toString(Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val KEY_ALIAS = "classcue_api_key_v1"
        private const val LEGACY_KEY = "api_key"
        private const val DEFAULT_PROFILE_ID = "default_fast"
    }
}
