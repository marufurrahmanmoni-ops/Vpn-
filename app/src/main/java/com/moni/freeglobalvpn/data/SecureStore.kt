package com.moni.freeglobalvpn.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted file store (AES-256-GCM, key held in the Android Keystore and never exported).
 * Used for imported VPN configs and credentials. Files live in app-private storage and the app
 * opts out of Android backup, so they never leave the device.
 */
class SecureStore(context: Context) {
    private val dir = File(context.filesDir, "secure").apply { mkdirs() }

    private val key: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }.generateKey()
    }

    @Synchronized
    fun write(name: String, plain: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val enc = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(c.iv + enc)
        tmp.renameTo(File(dir, name))
    }

    @Synchronized
    fun read(name: String): String? {
        val f = File(dir, name)
        if (!f.exists()) return null
        return runCatching {
            val all = f.readBytes()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all, 0, 12))
            String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    private companion object { const val ALIAS = "fgv_secure_store_v1" }
}
