package com.jinatra.wend.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DataUnavailable(message: String) : IllegalStateException(message)

internal class DatabaseKeyVault(private val context: Context, private val name: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, name + ".key"))
    private val alias = "wend-database-wrap-v1-" + name
    private val aad = ("Wend database key v1:" + name).toByteArray(Charsets.UTF_8)

    fun loadOrCreate(databaseExists: Boolean): ByteArray = synchronized(lock) {
        val existing = try {
            file.openRead().use { it.readBytes() }
        } catch (_: java.io.FileNotFoundException) {
            null
        }
        if (existing != null) {
            check(existing.size == 61 && existing[0] == 1.toByte()) { "Invalid wrapped database key" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, wrappingKey(false), GCMParameterSpec(128, existing.copyOfRange(1, 13)))
                updateAAD(aad)
            }
            return@synchronized cipher.doFinal(existing.copyOfRange(13, existing.size)).also { check(it.size == 32) }
        }
        check(!databaseExists && !file.baseFile.exists()) { "Database key unavailable; existing data was preserved" }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, wrappingKey(true))
                updateAAD(aad)
            }
            check(cipher.iv.size == 12)
            val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(secret)
            val output = file.startWrite()
            try {
                output.write(encrypted)
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
            secret
        } catch (error: Throwable) {
            secret.fill(0)
            throw error
        }
    }

    private fun wrappingKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Database wrapping key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
            )
        }.generateKey()
    }

    fun delete() = synchronized(lock) {
        file.delete()
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
    }

    private companion object {
        val lock = Any()
    }
}
