package com.example.security

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object FileSecurityUtils {

    /**
     * Sanitizes filename to prevent directory traversal and illegal characters.
     */
    fun sanitizeFileName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "file_${System.currentTimeMillis()}"

        // Remove path components
        val fileNameOnly = File(rawName).name

        // Replace forbidden characters and path traversal patterns
        val sanitized = fileNameOnly
            .replace("\\", "_")
            .replace("/", "_")
            .replace("..", "_")
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
            .trim()

        return if (sanitized.isBlank()) "file_${System.currentTimeMillis()}" else sanitized
    }

    /**
     * Resolves collisions by appending (1), (2), etc.
     */
    fun resolveUniqueFile(directory: File, fileName: String): File {
        val cleanName = sanitizeFileName(fileName)
        var target = File(directory, cleanName)
        if (!target.exists()) return target

        val nameWithoutExt = cleanName.substringBeforeLast('.', cleanName)
        val ext = if (cleanName.contains('.')) "." + cleanName.substringAfterLast('.') else ""

        var counter = 1
        while (target.exists()) {
            target = File(directory, "$nameWithoutExt ($counter)$ext")
            counter++
        }
        return target
    }

    /**
     * Computes SHA-256 checksum of an InputStream.
     */
    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Computes SHA-256 of a local file.
     */
    fun computeFileSha256(file: File): String {
        return file.inputStream().use { computeSha256(it) }
    }

    /**
     * Computes SHA-256 for a Content Uri.
     */
    fun computeUriSha256(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use {
            computeSha256(it)
        } ?: ""
    }
}
