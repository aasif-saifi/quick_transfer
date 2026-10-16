package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.network.FileTransferMeta
import com.example.network.HandshakeRequest
import com.example.network.HandshakeResponse
import com.example.network.TransferProtocol
import com.example.security.PairingSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Quick Transfer", appName)
    }

    @Test
    fun testPairingSecurityPayload() {
        val session = PairingSecurity.generateSession(
            deviceName = "Pixel 9",
            ipAddress = "192.168.1.50",
            port = 8888
        )
        assertEquals(6, session.pin.length)
        assertTrue(session.pin.all { it.isDigit() })

        val qrPayload = PairingSecurity.createQrPayload(session)
        assertTrue(qrPayload.startsWith("quicktransfer://pair"))

        val parsed = PairingSecurity.parseQrPayload(qrPayload)
        assertNotNull(parsed)
        assertEquals(session.pin, parsed?.pin)
        assertEquals(session.ipAddress, parsed?.ipAddress)
        assertEquals(session.port, parsed?.port)
        assertEquals(session.deviceName, parsed?.deviceName)
    }

    @Test
    fun testTransferProtocolFraming() {
        val metaList = listOf(
            FileTransferMeta(
                id = "f1",
                name = "holiday.jpg",
                size = 1024 * 1024,
                mimeType = "image/jpeg",
                checksumSha256 = "abc123"
            )
        )
        val request = HandshakeRequest(
            senderName = "Galaxy S24",
            sessionId = "session-123",
            pin = "654321",
            files = metaList
        )
        val serialized = TransferProtocol.serializeHandshakeRequest(request)
        val deserialized = TransferProtocol.parseHandshakeRequest(serialized)

        assertEquals("Galaxy S24", deserialized.senderName)
        assertEquals("session-123", deserialized.sessionId)
        assertEquals("654321", deserialized.pin)
        assertEquals(1, deserialized.files.size)
        assertEquals("holiday.jpg", deserialized.files[0].name)
        assertEquals(1024 * 1024L, deserialized.files[0].size)

        val response = HandshakeResponse(accepted = true, reason = "Ready", resumeOffsets = mapOf("f1" to 512L))
        val respJson = TransferProtocol.serializeHandshakeResponse(response)
        val parsedResp = TransferProtocol.parseHandshakeResponse(respJson)
        assertTrue(parsedResp.accepted)
        assertEquals("Ready", parsedResp.reason)
        assertEquals(512L, parsedResp.resumeOffsets["f1"])
    }
}
