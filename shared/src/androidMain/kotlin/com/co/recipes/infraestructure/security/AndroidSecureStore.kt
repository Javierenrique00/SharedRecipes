package com.co.recipes.infraestructure.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android secret store: values are encrypted with AES-256-GCM under a non-exportable
 * key held by the Android Keystore, then written to a private SharedPreferences file.
 *
 * We deliberately do **not** use `androidx.security:security-crypto`
 * (EncryptedSharedPreferences) — that artifact is deprecated and only ships alpha
 * releases, while the Keystore API used here is stable and needs no dependency.
 *
 * If the Keystore key is invalidated (backup restore, some OEM lock-screen changes)
 * decryption fails and [get] returns null: the user signs in again instead of the app
 * crashing in a loop.
 */
internal class AndroidSecureStore(context: Context) : SecureStore {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val stored = preferences.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    override fun put(key: String, value: String) {
        // commit(), not apply(): the session must be on disk before the process dies.
        preferences.edit().putString(key, encrypt(value)).commit()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        // iv || ciphertext, Base64 for a single preferences value.
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH_BYTES) { "ciphertext too short" }
        val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }

    /** Returns the Keystore key, generating it on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_session"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "recipes_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}

/**
 * Hands the Android `applicationContext` to [createSecureStore].
 *
 * Called from `ComponentActivity.startSharedRecipes()` before the Compose tree is
 * attached, which is the same place the Activity bridge is set up — no manifest
 * provider or `androidx.startup` entry needed.
 */
object SecureStoreInitializer {

    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun requireContext(): Context =
        applicationContext ?: error(
            "SecureStoreInitializer.init(applicationContext) must run before App(). " +
                "Call ComponentActivity.startSharedRecipes() instead of setting the content view directly.",
        )
}

internal actual fun createSecureStore(): SecureStore =
    AndroidSecureStore(SecureStoreInitializer.requireContext())
