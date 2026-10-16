package com.example

import com.example.file.FileManager
import com.example.security.FileSecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {

    @Test
    fun testFileSecuritySanitization() {
        val dangerous = "../../etc/passwd"
        val sanitized = FileSecurityUtils.sanitizeFileName(dangerous)
        assertEquals("passwd", sanitized)

        val nullName: String? = null
        val defaultSanitized = FileSecurityUtils.sanitizeFileName(nullName)
        assertTrue(defaultSanitized.startsWith("file_"))

        val dirtyName = "report..//..final.pdf"
        val clean = FileSecurityUtils.sanitizeFileName(dirtyName)
        assertFalse(clean.contains(".."))
        assertFalse(clean.contains("/"))
    }

    @Test
    fun testSha256Checksum() {
        val input = "Hello Quick Transfer!"
        val stream = ByteArrayInputStream(input.toByteArray(Charsets.UTF_8))
        val sha = FileSecurityUtils.computeSha256(stream)
        assertNotNull(sha)
        assertEquals(64, sha.length) // 256 bits = 64 hex characters
    }

    @Test
    fun testFormattingUtilities() {
        assertEquals("0 B", FileManager.formatFileSize(0))
        assertEquals("1.0 KB", FileManager.formatFileSize(1024))
        assertEquals("1.5 MB", FileManager.formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", FileManager.formatFileSize(2L * 1024 * 1024 * 1024))

        assertEquals("5.0 MB/s", FileManager.formatSpeed((5.0 * 1024 * 1024).toLong()))
        assertEquals("45s", FileManager.formatEta(45))
        assertEquals("2m 10s", FileManager.formatEta(130))
    }
}
