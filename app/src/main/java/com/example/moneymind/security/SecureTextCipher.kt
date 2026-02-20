package com.example.moneymind.security
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTextCipher {
    fun encrypt(value: String?): String? {
        val plain = value?.trim().orEmpty()
        if (plain.isEmpty()) return null
        if (plain.startsWith(PREFIX)) return plain

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, payload, 0, iv.size)
            System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
            PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        }.getOrElse { plain }
    }

    fun decrypt(value: String?): String? {
        val encrypted = value?.trim().orEmpty()
        if (encrypted.isEmpty()) return null
        if (!encrypted.startsWith(PREFIX)) return encrypted

        return runCatching {
            val raw = encrypted.removePrefix(PREFIX)
            val payload = Base64.decode(raw, Base64.NO_WRAP)
            if (payload.size <= IV_LENGTH) return@runCatching encrypted

            val iv = payload.copyOfRange(0, IV_LENGTH)
            val ciphertext = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrElse { encrypted }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "moneymind_secure_text_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "enc::"
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
