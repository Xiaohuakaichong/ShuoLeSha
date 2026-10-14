package com.example.shuolesa.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials stay encrypted at rest and are excluded from device/cloud backups. */
class SecretStore {
    @Synchronized private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun encrypt(value: String): String {
        if(value.isBlank()) return ""
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val body=cipher.doFinal(value.toByteArray())
        return "${Base64.encodeToString(cipher.iv,Base64.NO_WRAP)}:${Base64.encodeToString(body,Base64.NO_WRAP)}"
    }
    fun decrypt(value: String?): String = runCatching {
        if(value.isNullOrBlank()) return ""
        val parts=value.split(':',limit=2)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
        }
        String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)))
    }.getOrDefault("")
    companion object { private const val ALIAS="shuolesha.credentials.v4" }
}
