package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TransferWithFiles(
    val transfer: TransferRecordEntity,
    val files: List<TransferFileEntity>
)

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getTransferById(id: Long): TransferRecordEntity?

    @Query("SELECT * FROM transfer_files WHERE transferRecordId = :transferId")
    fun getFilesForTransfer(transferId: Long): Flow<List<TransferFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<TransferFileEntity>)

    @Query("UPDATE transfers SET status = :status, transferredBytes = :transferredBytes, durationMillis = :durationMillis, errorReason = :error WHERE id = :id")
    suspend fun updateTransferStatus(id: Long, status: String, transferredBytes: Long, durationMillis: Long, error: String?)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteTransfer(id: Long)

    @Query("DELETE FROM transfer_files WHERE transferRecordId = :transferId")
    suspend fun deleteFilesForTransfer(transferId: Long)

    @Transaction
    suspend fun deleteTransferAndFiles(transferId: Long) {
        deleteFilesForTransfer(transferId)
        deleteTransfer(transferId)
    }

    @Query("DELETE FROM transfers")
    suspend fun clearAllTransfers()

    @Query("DELETE FROM transfer_files")
    suspend fun clearAllFiles()

    @Transaction
    suspend fun clearAllHistory() {
        clearAllFiles()
        clearAllTransfers()
    }
}
