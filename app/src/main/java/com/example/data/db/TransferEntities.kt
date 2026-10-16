package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val peerName: String,
    val peerAddress: String,
    val direction: String, // SEND or RECEIVE
    val totalBytes: Long,
    val transferredBytes: Long,
    val fileCount: Int,
    val status: String, // COMPLETED, FAILED, CANCELLED
    val timestamp: Long = System.currentTimeMillis(),
    val durationMillis: Long = 0L,
    val errorReason: String? = null
)

@Entity(tableName = "transfer_files")
data class TransferFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transferRecordId: Long,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val localPath: String,
    val status: String,
    val checksumSha256: String = ""
)
