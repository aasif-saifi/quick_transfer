package com.example.network

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Finds the primary local IPv4 address of this device (Wi-Fi, hotspot, or P2P interface).
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            val ipCandidates = mutableListOf<String>()

            for (networkInterface in interfaces) {
                // Ignore loopback and down interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val name = networkInterface.name.lowercase()
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val host = address.hostAddress ?: continue
                        // Prioritize wlan (Wi-Fi / Hotspot) and p2p interfaces
                        if (name.contains("wlan") || name.contains("p2p") || name.contains("ap")) {
                            return host
                        }
                        ipCandidates.add(host)
                    }
                }
            }

            return ipCandidates.firstOrNull() ?: "127.0.0.1"
        } catch (_: Exception) {
            return "127.0.0.1"
        }
    }
}
