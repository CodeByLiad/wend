package com.jinatra.wend.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ImportedModel(val id: String, val file: File, val sha256: String, val bytes: Long)

/** Local Storage Access Framework import only. No downloader or network permission. */
object ModelImport {
    suspend fun copy(context: Context, uri: Uri): ImportedModel = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "models").apply { mkdirs() }
        val destination = File(directory, "$id.model")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                destination.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(destination.length() > 0)
            ImportedModel(id, destination, digest.digest().joinToString("") { "%02x".format(it) }, destination.length())
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }
}
