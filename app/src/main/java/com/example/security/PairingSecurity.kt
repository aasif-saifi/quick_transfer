package com.example.security

import android.net.Uri
import com.example.domain.model.PairingSession
import java.security.SecureRandom
import java.util.UUID

object PairingSecurity {
    private val secureRandom = SecureRandom()

    fun generateSession(deviceName: String, ipAddress: String, port: Int): PairingSession {
        val pin = String.format("%06d", secureRandom.nextInt(1_000_000))
        val sessionId = UUID.randomUUID().toString()
        return PairingSession(
            sessionId = sessionId,
            pin = pin,
            ipAddress = ipAddress,
            port = port,
            deviceName = deviceName
        )
    }

    /**
     * Encodes pairing session into a secure QR URL payload.
     * Schema: quicktransfer://pair?session=...&pin=...&ip=...&port=...&name=...
     */
    fun createQrPayload(session: PairingSession): String {
        return Uri.Builder()
            .scheme("quicktransfer")
            .authority("pair")
            .appendQueryParameter("session", session.sessionId)
            .appendQueryParameter("pin", session.pin)
            .appendQueryParameter("ip", session.ipAddress)
            .appendQueryParameter("port", session.port.toString())
            .appendQueryParameter("name", session.deviceName)
            .build()
            .toString()
    }

    /**
     * Parses QR URI payload safely.
     */
    fun parseQrPayload(raw: String): PairingSession? {
        return try {
            val uri = Uri.parse(raw)
            if (uri.scheme != "quicktransfer" || uri.authority != "pair") return null

            val session = uri.getQueryParameter("session") ?: return null
            val pin = uri.getQueryParameter("pin") ?: return null
            val ip = uri.getQueryParameter("ip") ?: return null
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8888
            val name = uri.getQueryParameter("name") ?: "Nearby Device"

            PairingSession(
                sessionId = session,
                pin = pin,
                ipAddress = ip,
                port = port,
                deviceName = name
            )
        } catch (_: Exception) {
            null
        }
    }
}
