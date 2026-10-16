package com.example.data.repository

import com.example.data.db.TransferDao
import com.example.data.db.TransferFileEntity
import com.example.data.db.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

class TransferRepository(private val dao: TransferDao) {
    val allTransfers: Flow<List<TransferRecordEntity>> = dao.getAllTransfers()

    fun getFilesForTransfer(transferId: Long): Flow<List<TransferFileEntity>> {
        return dao.getFilesForTransfer(transferId)
    }

    suspend fun recordTransferStart(
        sessionId: String,
        peerName: String,
        peerAddress: String,
        direction: String,
        totalBytes: Long,
        files: List<TransferFileEntity>
    ): Long {
        val transferId = dao.insertTransfer(
            TransferRecordEntity(
                sessionId = sessionId,
                peerName = peerName,
                peerAddress = peerAddress,
                direction = direction,
                totalBytes = totalBytes,
                transferredBytes = 0L,
                fileCount = files.size,
                status = "IN_PROGRESS",
                timestamp = System.currentTimeMillis()
            )
        )
        val filesWithId = files.map { it.copy(transferRecordId = transferId) }
        dao.insertFiles(filesWithId)
        return transferId
    }

    suspend fun updateTransferFinish(
        transferId: Long,
        status: String,
        transferredBytes: Long,
        durationMillis: Long,
        error: String? = null
    ) {
        dao.updateTransferStatus(transferId, status, transferredBytes, durationMillis, error)
    }

    suspend fun deleteTransfer(transferId: Long) {
        dao.deleteTransferAndFiles(transferId)
    }

    suspend fun clearHistory() {
        dao.clearAllHistory()
    }
}
