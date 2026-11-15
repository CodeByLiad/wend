package com.jinatra.wend.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ImportedModel(val id: String, val file: File, val sha256: String, val bytes: Long)

/** Local Storage Access Framework import only. No downloader or network permission. */
object ModelImport {
    suspend fun copy(context: Context, uri: Uri): ImportedModel {
        // Keep ownership until withContext delivers the result to its caller. Cancellation
        // can discard an already completed IO result while dispatching back to the UI.
        var destination: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val id = UUID.randomUUID().toString()
                val directory = File(context.filesDir, "models").apply { mkdirs() }
                var outputFile = File(directory, "$id.model")
                destination = outputFile
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    outputFile.outputStream().use { output ->
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
                require(outputFile.length() > 0)
                val header = ByteArray(8)
                outputFile.inputStream().use { it.read(header) }
                val signature = String(header, Charsets.US_ASCII)
                val suffix = when {
                    signature.startsWith("LITERTLM") -> "litertlm"
                    signature.startsWith("GGUF") -> "gguf"
                    else -> "model"
                }
                val typedDestination = File(directory, "$id.$suffix")
                if (typedDestination != outputFile) {
                    check(outputFile.renameTo(typedDestination))
                    outputFile = typedDestination
                    destination = typedDestination
                }
                ImportedModel(id, outputFile, digest.digest().joinToString("") { "%02x".format(it) }, outputFile.length())
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { destination?.delete() }
            throw error
        }
    }
}
