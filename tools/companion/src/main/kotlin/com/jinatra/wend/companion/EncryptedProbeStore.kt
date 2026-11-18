package com.jinatra.wend.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ProbeStore {
    fun read(): ProbeData
    fun write(data: ProbeData)
    fun delete()
}

/** Only ciphertext reaches disk. AtomicFile keeps the last complete encrypted snapshot on a failed write. */
class EncryptedProbeStore(context: Context, private val alias: String = "wend-phase0-companion-v1") : ProbeStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "companion-v1.enc"))
    private val aad = "Wend companion probe v1".toByteArray(Charsets.UTF_8)

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Encryption key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
        }.generateKey()
    }

    override fun read(): ProbeData {
        val bytes = try {
            require(file.baseFile.length() <= 20 * 1024 * 1024)
            file.openRead().use { stream ->
                val data = stream.readBytes()
                require(data.size <= 20 * 1024 * 1024)
                data
            }
        } catch (error: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
            return ProbeData()
        }
        require(bytes.size >= 29 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            updateAAD(aad)
        }
        return decode(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8))
    }

    override fun write(data: ProbeData) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(true))
            updateAAD(aad)
        }
        check(cipher.iv.size == 12)
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(encode(data).toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    override fun delete() {
        file.delete()
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
    }
}

private fun encode(data: ProbeData): String {
    fun record(id: String, createdAt: Long, updatedAt: Long, text: String) = JSONObject().put("id", id).put("createdAt", createdAt).put("updatedAt", updatedAt).put("text", text)
    return JSONObject().put("version", 1)
        .put("messages", JSONArray(data.messages.map { record(it.id, it.createdAt, it.updatedAt, it.text).put("speaker", it.speaker.name) }))
        .put("facts", JSONArray(data.facts.map { record(it.id, it.createdAt, it.updatedAt, it.text) })).toString()
}

private fun decode(text: String): ProbeData {
    val root = JSONObject(text)
    require(root.getInt("version") == 1)
    val messages = root.getJSONArray("messages")
    val facts = root.getJSONArray("facts")
    require(messages.length() <= MAX_MESSAGES && facts.length() <= MAX_FACTS)
    return ProbeData(
        (0 until messages.length()).map { index ->
            val item = messages.getJSONObject(index)
            ProbeMessage(item.getString("id"), item.getLong("createdAt"), item.getLong("updatedAt"), Speaker.valueOf(item.getString("speaker")), item.getString("text"))
        },
        (0 until facts.length()).map { index ->
            val item = facts.getJSONObject(index)
            ProbeFact(item.getString("id"), item.getLong("createdAt"), item.getLong("updatedAt"), item.getString("text"))
        },
    )
}
