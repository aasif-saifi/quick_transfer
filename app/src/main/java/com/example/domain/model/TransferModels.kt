package com.example.domain.model

import android.net.Uri

enum class TransferDirection {
    SEND,
    RECEIVE
}

enum class TransferState {
    IDLE,
    CONNECTING,
    WAITING_APPROVAL,
    TRANSFERRING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri? = null,
    val bytesTransferred: Long = 0L,
    val isComplete: Boolean = false,
    val checksumSha256: String = "",
    val error: String? = null
)

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val transportType: TransportType = TransportType.WIFI_LOCAL,
    val rssi: Int? = null,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

enum class TransportType {
    WIFI_DIRECT,
    WIFI_LOCAL,
    BLUETOOTH
}

data class PairingSession(
    val sessionId: String,
    val pin: String,
    val ipAddress: String,
    val port: Int,
    val deviceName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutes
)

data class TransferProgress(
    val transferId: String,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val currentFileBytes: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val overallBytesTransferred: Long = 0L,
    val overallTotalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val state: TransferState = TransferState.IDLE,
    val errorMessage: String? = null
)
