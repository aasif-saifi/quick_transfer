package com.example.file

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.domain.model.TransferFileItem
import com.example.security.FileSecurityUtils
import java.io.File
import java.util.Locale
import java.util.UUID

enum class FileCategory(val title: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APKS("APKs"),
    ARCHIVES("Archives"),
    OTHER("Other")
}

data class LocalReceivedFile(
    val file: File,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: FileCategory,
    val lastModified: Long,
    val uri: Uri
)

class FileManager(private val context: Context) {

    private val receivedDir: File by lazy {
        val external = context.getExternalFilesDir("received")
        if (external != null && external.exists()) {
            external
        } else {
            val dir = File(context.filesDir, "received")
            if (!dir.exists()) dir.mkdirs()
            dir
        }
    }

    fun getReceivedDirectory(): File = receivedDir

    /**
     * Resolves metadata from a picked Content Uri.
     */
    fun resolveUriMetadata(uri: Uri): TransferFileItem {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L

        try {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // fallback
            uri.lastPathSegment?.let { name = it }
        }

        val cleanName = FileSecurityUtils.sanitizeFileName(name)
        val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(cleanName)

        return TransferFileItem(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            size = size,
            mimeType = mimeType,
            uri = uri
        )
    }

    fun getCategory(fileName: String, mimeType: String): FileCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = mimeType.lowercase(Locale.ROOT)

        return when {
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic") -> FileCategory.PHOTOS
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp") -> FileCategory.VIDEOS
            mime.startsWith("audio/") || ext in listOf("mp3", "wav", "m4a", "flac", "ogg", "aac") -> FileCategory.AUDIO
            ext == "apk" || mime == "application/vnd.android.package-archive" -> FileCategory.APKS
            ext in listOf("zip", "rar", "7z", "tar", "gz") -> FileCategory.ARCHIVES
            mime.startsWith("text/") || ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md") -> FileCategory.DOCUMENTS
            else -> FileCategory.OTHER
        }
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.ROOT))
            ?: "application/octet-stream"
    }

    fun listReceivedFiles(): List<LocalReceivedFile> {
        val files = receivedDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && !it.name.endsWith(".qktmp") }
            .map { file ->
                val mime = getMimeTypeFromExtension(file.name)
                val cat = getCategory(file.name, mime)
                val uri = try {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } catch (_: Exception) {
                    Uri.fromFile(file)
                }
                LocalReceivedFile(
                    file = file,
                    name = file.name,
                    size = file.length(),
                    mimeType = mime,
                    category = cat,
                    lastModified = file.lastModified(),
                    uri = uri
                )
            }
            .sortedByDescending { it.lastModified }
    }

    fun openFile(localFile: LocalReceivedFile) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(localFile.uri, localFile.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Try generic fallback
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(localFile.uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallback, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun shareFile(localFile: LocalReceivedFile) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = localFile.mimeType
            putExtra(Intent.EXTRA_STREAM, localFile.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${localFile.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun deleteFile(localFile: LocalReceivedFile): Boolean {
        return try {
            localFile.file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun renameFile(localFile: LocalReceivedFile, newName: String): Boolean {
        val sanitized = FileSecurityUtils.sanitizeFileName(newName)
        val target = File(receivedDir, sanitized)
        if (target.exists()) return false
        return localFile.file.renameTo(target)
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, index.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[index])
        }

        fun formatSpeed(bytesPerSec: Long): String {
            return "${formatFileSize(bytesPerSec)}/s"
        }

        fun formatEta(seconds: Long): String {
            if (seconds <= 0) return "--"
            return when {
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
                else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            }
        }
    }
}
