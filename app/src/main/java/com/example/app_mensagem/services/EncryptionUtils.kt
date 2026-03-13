package com.example.app_mensagem.services

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES"
    // Nota: Em um app real, esta chave deve ser gerenciada com segurança (Keystore)
    private const val TRANSFORMATION = "AES"
    private val key = SecretKeySpec("1234567890123456".toByteArray(), ALGORITHM)

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedByte = cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(encryptedByte, Base64.DEFAULT)
    }

    fun decrypt(value: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decodedByte = Base64.decode(value, Base64.DEFAULT)
            String(cipher.doFinal(decodedByte))
        } catch (e: Exception) {
            value // Retorna o valor original se houver falha (ex: mensagem legada)
        }
    }
}