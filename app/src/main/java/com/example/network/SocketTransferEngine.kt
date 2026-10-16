package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.domain.model.TransferFileItem
import com.example.domain.model.TransferProgress
import com.example.domain.model.TransferState
import com.example.file.FileManager
import com.example.security.FileSecurityUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID

sealed class TransferEvent {
    data class IncomingRequest(
        val sessionId: String,
        val senderName: String,
        val senderIp: String,
        val files: List<FileTransferMeta>,
        val totalBytes: Long,
        val onDecision: (Boolean) -> Unit
    ) : TransferEvent()

    data class Completed(
        val transferId: String,
        val peerName: String,
        val totalBytes: Long,
        val filesCount: Int,
        val durationMillis: Long
    ) : TransferEvent()

    data class Failed(
        val transferId: String,
        val peerName: String,
        val error: String
    ) : TransferEvent()
}

class SocketTransferEngine(private val context: Context) {
    private val TAG = "SocketTransferEngine"
    private val fileManager = FileManager(context)

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var transferJob: Job? = null

    private val _transferProgress = MutableStateFlow(TransferProgress(""))
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    private val _events = MutableSharedFlow<TransferEvent>(replay = 0)
    val events: SharedFlow<TransferEvent> = _events.asSharedFlow()

    @Volatile
    private var isCancelled = false

    @Volatile
    private var isPaused = false

    // --- RECEIVER SERVER METHODS ---

    fun startServer(
        port: Int = 8888,
        expectedPin: String = "",
        autoAccept: Boolean = false
    ) {
        stopServer()
        engineScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                Log.d(TAG, "Server socket bound on port $port")

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")
                    handleIncomingClient(clientSocket, expectedPin, autoAccept)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    private fun handleIncomingClient(socket: Socket, expectedPin: String, autoAccept: Boolean) {
        transferJob?.cancel()
        transferJob = engineScope.launch {
            activeSocket = socket
            isCancelled = false
            isPaused = false

            var dis: DataInputStream? = null
            var dos: DataOutputStream? = null

            try {
                socket.soTimeout = 30000 // 30 sec timeout for socket operations
                dis = DataInputStream(socket.getInputStream())
                dos = DataOutputStream(socket.getOutputStream())

                // 1. Validate magic header
                val magic = dis.readInt()
                if (magic != TransferProtocol.MAGIC_HEADER) {
                    dos.writeInt(TransferProtocol.TYPE_HANDSHAKE_RESP)
                    dos.writeUTF(
                        TransferProtocol.serializeHandshakeResponse(
                            HandshakeResponse(false, "Invalid protocol")
                        )
                    )
                    socket.close()
                    return@launch
                }

                // 2. Read Handshake Request
                val msgType = dis.readInt()
                if (msgType != TransferProtocol.TYPE_HANDSHAKE_REQ) {
                    socket.close()
                    return@launch
                }
                val handshakeJson = dis.readUTF()
                val req = TransferProtocol.parseHandshakeRequest(handshakeJson)

                // Check PIN if expectedPin is configured
                if (expectedPin.isNotBlank() && req.pin.isNotBlank() && req.pin != expectedPin) {
                    dos.writeInt(TransferProtocol.TYPE_HANDSHAKE_RESP)
                    dos.writeUTF(
                        TransferProtocol.serializeHandshakeResponse(
                            HandshakeResponse(false, "PIN mismatch")
                        )
                    )
                    socket.close()
                    return@launch
                }

                val totalBytes = req.files.sumOf { it.size }
                val sessionId = req.sessionId
                val peerName = req.senderName
                val peerIp = socket.inetAddress?.hostAddress ?: ""

                // 3. User Decision or Auto Accept
                var approved = autoAccept
                if (!autoAccept) {
                    _transferProgress.value = TransferProgress(
                        transferId = sessionId,
                        totalFiles = req.files.size,
                        overallTotalBytes = totalBytes,
                        state = TransferState.WAITING_APPROVAL
                    )

                    val decisionDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    _events.emit(
                        TransferEvent.IncomingRequest(
                            sessionId = sessionId,
                            senderName = peerName,
                            senderIp = peerIp,
                            files = req.files,
                            totalBytes = totalBytes,
                            onDecision = { decision -> decisionDeferred.complete(decision) }
                        )
                    )
                    approved = decisionDeferred.await()
                }

                if (!approved) {
                    dos.writeInt(TransferProtocol.TYPE_HANDSHAKE_RESP)
                    dos.writeUTF(
                        TransferProtocol.serializeHandshakeResponse(
                            HandshakeResponse(false, "Rejected by receiver")
                        )
                    )
                    dos.flush()
                    socket.close()
                    _transferProgress.value = TransferProgress(
                        transferId = sessionId,
                        state = TransferState.CANCELLED,
                        errorMessage = "Transfer declined"
                    )
                    return@launch
                }

                // Send Approval
                dos.writeInt(TransferProtocol.TYPE_HANDSHAKE_RESP)
                dos.writeUTF(
                    TransferProtocol.serializeHandshakeResponse(
                        HandshakeResponse(true, "Accepted")
                    )
                )
                dos.flush()

                // Reset timeout for streaming data
                socket.soTimeout = 60000

                // 4. Stream Files
                val startTime = System.currentTimeMillis()
                var overallReceived = 0L
                val receivedDir = fileManager.getReceivedDirectory()

                _transferProgress.value = TransferProgress(
                    transferId = sessionId,
                    totalFiles = req.files.size,
                    overallTotalBytes = totalBytes,
                    state = TransferState.TRANSFERRING
                )

                for (i in req.files.indices) {
                    if (isCancelled) break

                    val headerType = dis.readInt()
                    if (headerType == TransferProtocol.TYPE_CANCEL) {
                        isCancelled = true
                        break
                    }
                    if (headerType != TransferProtocol.TYPE_FILE_HEADER) {
                        throw IllegalStateException("Unexpected frame type: $headerType")
                    }

                    val fileId = dis.readUTF()
                    val originalFileName = dis.readUTF()
                    val fileSize = dis.readLong()
                    val offset = dis.readLong()

                    val meta = req.files.getOrNull(i) ?: req.files[0]
                    val uniqueTargetFile = FileSecurityUtils.resolveUniqueFile(receivedDir, originalFileName)
                    val tempFile = File(receivedDir, "${uniqueTargetFile.name}.qktmp")

                    val fos = FileOutputStream(tempFile, offset > 0)
                    val digest = MessageDigest.getInstance("SHA-256")

                    var fileBytesReceived = offset
                    val buffer = ByteArray(TransferProtocol.CHUNK_SIZE)
                    var lastSpeedCalcTime = System.currentTimeMillis()
                    var bytesSinceLastCalc = 0L

                    while (fileBytesReceived < fileSize && !isCancelled) {
                        while (isPaused && !isCancelled) {
                            _transferProgress.value = _transferProgress.value.copy(state = TransferState.PAUSED)
                            kotlinx.coroutines.delay(200)
                        }

                        val chunkType = dis.readInt()
                        if (chunkType == TransferProtocol.TYPE_CANCEL) {
                            isCancelled = true
                            break
                        }
                        if (chunkType != TransferProtocol.TYPE_FILE_CHUNK) {
                            throw IllegalStateException("Expected FILE_CHUNK, got: $chunkType")
                        }

                        val chunkSize = dis.readInt()
                        dis.readFully(buffer, 0, chunkSize)
                        fos.write(buffer, 0, chunkSize)
                        digest.update(buffer, 0, chunkSize)

                        fileBytesReceived += chunkSize
                        overallReceived += chunkSize
                        bytesSinceLastCalc += chunkSize

                        val now = System.currentTimeMillis()
                        val elapsedCalc = now - lastSpeedCalcTime
                        var speed = _transferProgress.value.speedBytesPerSec
                        if (elapsedCalc >= 500) {
                            speed = (bytesSinceLastCalc * 1000) / elapsedCalc
                            lastSpeedCalcTime = now
                            bytesSinceLastCalc = 0L
                        }

                        val remainingBytes = (totalBytes - overallReceived).coerceAtLeast(0)
                        val eta = if (speed > 0) remainingBytes / speed else 0L

                        _transferProgress.value = TransferProgress(
                            transferId = sessionId,
                            currentFileIndex = i + 1,
                            totalFiles = req.files.size,
                            currentFileName = uniqueTargetFile.name,
                            currentFileBytes = fileBytesReceived,
                            currentFileTotalBytes = fileSize,
                            overallBytesTransferred = overallReceived,
                            overallTotalBytes = totalBytes,
                            speedBytesPerSec = speed,
                            etaSeconds = eta,
                            state = TransferState.TRANSFERRING
                        )
                    }

                    fos.flush()
                    fos.close()

                    if (isCancelled) {
                        tempFile.delete()
                        break
                    }

                    // Read File End Frame
                    val endType = dis.readInt()
                    if (endType == TransferProtocol.TYPE_FILE_END) {
                        val senderSha256 = dis.readUTF()
                        val computedSha = digest.digest().joinToString("") { "%02x".format(it) }

                        // Check integrity if sender provided checksum
                        val checksumOk = senderSha256.isBlank() || senderSha256.equals(computedSha, ignoreCase = true)
                        if (checksumOk && tempFile.length() == fileSize) {
                            tempFile.renameTo(uniqueTargetFile)
                            dos.writeInt(TransferProtocol.TYPE_FILE_ACK)
                            dos.writeBoolean(true)
                            dos.flush()
                        } else {
                            tempFile.delete()
                            dos.writeInt(TransferProtocol.TYPE_FILE_ACK)
                            dos.writeBoolean(false)
                            dos.flush()
                            throw IllegalStateException("Checksum verification failed for ${uniqueTargetFile.name}")
                        }
                    }
                }

                if (isCancelled) {
                    _transferProgress.value = _transferProgress.value.copy(
                        state = TransferState.CANCELLED,
                        errorMessage = "Transfer cancelled"
                    )
                    _events.emit(TransferEvent.Failed(sessionId, peerName, "Cancelled by user"))
                } else {
                    val duration = System.currentTimeMillis() - startTime
                    _transferProgress.value = TransferProgress(
                        transferId = sessionId,
                        currentFileIndex = req.files.size,
                        totalFiles = req.files.size,
                        overallBytesTransferred = overallReceived,
                        overallTotalBytes = totalBytes,
                        state = TransferState.COMPLETED
                    )
                    _events.emit(
                        TransferEvent.Completed(
                            transferId = sessionId,
                            peerName = peerName,
                            totalBytes = overallReceived,
                            filesCount = req.files.size,
                            durationMillis = duration
                        )
                    )
                }

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Receive error", e)
                    _transferProgress.value = _transferProgress.value.copy(
                        state = TransferState.FAILED,
                        errorMessage = e.message ?: "Connection error"
                    )
                    _events.emit(TransferEvent.Failed(UUID.randomUUID().toString(), "Peer", e.message ?: "Transfer error"))
                }
            } finally {
                try { dis?.close() } catch (_: Exception) {}
                try { dos?.close() } catch (_: Exception) {}
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    // --- SENDER CLIENT METHODS ---

    fun sendFiles(
        targetIp: String,
        targetPort: Int,
        senderName: String,
        files: List<TransferFileItem>,
        pin: String = ""
    ) {
        transferJob?.cancel()
        transferJob = engineScope.launch {
            val sessionId = UUID.randomUUID().toString()
            val totalBytes = files.sumOf { it.size }
            isCancelled = false
            isPaused = false

            _transferProgress.value = TransferProgress(
                transferId = sessionId,
                totalFiles = files.size,
                overallTotalBytes = totalBytes,
                state = TransferState.CONNECTING
            )

            var socket: Socket? = null
            var dis: DataInputStream? = null
            var dos: DataOutputStream? = null

            try {
                socket = Socket()
                socket.connect(InetSocketAddress(targetIp, targetPort), 10000)
                socket.soTimeout = 30000
                activeSocket = socket

                dis = DataInputStream(socket.getInputStream())
                dos = DataOutputStream(socket.getOutputStream())

                // 1. Send Magic Header
                dos.writeInt(TransferProtocol.MAGIC_HEADER)

                // 2. Send Handshake Request
                val metaList = files.map {
                    FileTransferMeta(
                        id = it.id,
                        name = it.name,
                        size = it.size,
                        mimeType = it.mimeType,
                        checksumSha256 = it.checksumSha256
                    )
                }
                val handshakeReq = HandshakeRequest(
                    senderName = senderName,
                    sessionId = sessionId,
                    pin = pin,
                    files = metaList
                )
                dos.writeInt(TransferProtocol.TYPE_HANDSHAKE_REQ)
                dos.writeUTF(TransferProtocol.serializeHandshakeRequest(handshakeReq))
                dos.flush()

                _transferProgress.value = _transferProgress.value.copy(state = TransferState.WAITING_APPROVAL)

                // 3. Await Handshake Response
                val respType = dis.readInt()
                if (respType != TransferProtocol.TYPE_HANDSHAKE_RESP) {
                    throw IllegalStateException("Unexpected response type: $respType")
                }
                val respJson = dis.readUTF()
                val handshakeResp = TransferProtocol.parseHandshakeResponse(respJson)

                if (!handshakeResp.accepted) {
                    _transferProgress.value = TransferProgress(
                        transferId = sessionId,
                        state = TransferState.CANCELLED,
                        errorMessage = handshakeResp.reason.ifBlank { "Declined by recipient" }
                    )
                    _events.emit(TransferEvent.Failed(sessionId, targetIp, handshakeResp.reason))
                    return@launch
                }

                // Reset timeout for streaming
                socket.soTimeout = 60000
                val startTime = System.currentTimeMillis()
                var overallTransferred = 0L

                _transferProgress.value = TransferProgress(
                    transferId = sessionId,
                    totalFiles = files.size,
                    overallTotalBytes = totalBytes,
                    state = TransferState.TRANSFERRING
                )

                // 4. Stream Each File
                for (i in files.indices) {
                    if (isCancelled) break

                    val file = files[i]
                    val uri = file.uri ?: continue

                    // Send FILE_HEADER
                    dos.writeInt(TransferProtocol.TYPE_FILE_HEADER)
                    dos.writeUTF(file.id)
                    dos.writeUTF(file.name)
                    dos.writeLong(file.size)
                    dos.writeLong(0L) // offset 0
                    dos.flush()

                    val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot open input stream for ${file.name}")

                    val buffer = ByteArray(TransferProtocol.CHUNK_SIZE)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var fileBytesTransferred = 0L
                    var lastSpeedCalcTime = System.currentTimeMillis()
                    var bytesSinceLastCalc = 0L

                    inputStream.use { stream ->
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1 && !isCancelled) {
                            while (isPaused && !isCancelled) {
                                _transferProgress.value = _transferProgress.value.copy(state = TransferState.PAUSED)
                                kotlinx.coroutines.delay(200)
                            }

                            dos.writeInt(TransferProtocol.TYPE_FILE_CHUNK)
                            dos.writeInt(read)
                            dos.write(buffer, 0, read)
                            dos.flush()

                            digest.update(buffer, 0, read)
                            fileBytesTransferred += read
                            overallTransferred += read
                            bytesSinceLastCalc += read

                            val now = System.currentTimeMillis()
                            val elapsedCalc = now - lastSpeedCalcTime
                            var speed = _transferProgress.value.speedBytesPerSec
                            if (elapsedCalc >= 500) {
                                speed = (bytesSinceLastCalc * 1000) / elapsedCalc
                                lastSpeedCalcTime = now
                                bytesSinceLastCalc = 0L
                            }

                            val remaining = (totalBytes - overallTransferred).coerceAtLeast(0)
                            val eta = if (speed > 0) remaining / speed else 0L

                            _transferProgress.value = TransferProgress(
                                transferId = sessionId,
                                currentFileIndex = i + 1,
                                totalFiles = files.size,
                                currentFileName = file.name,
                                currentFileBytes = fileBytesTransferred,
                                currentFileTotalBytes = file.size,
                                overallBytesTransferred = overallTransferred,
                                overallTotalBytes = totalBytes,
                                speedBytesPerSec = speed,
                                etaSeconds = eta,
                                state = TransferState.TRANSFERRING
                            )
                        }
                    }

                    if (isCancelled) {
                        dos.writeInt(TransferProtocol.TYPE_CANCEL)
                        dos.flush()
                        break
                    }

                    // Send FILE_END with SHA-256
                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    dos.writeInt(TransferProtocol.TYPE_FILE_END)
                    dos.writeUTF(sha)
                    dos.flush()

                    // Await FILE_ACK
                    val ackType = dis.readInt()
                    if (ackType == TransferProtocol.TYPE_FILE_ACK) {
                        val success = dis.readBoolean()
                        if (!success) {
                            throw IllegalStateException("Remote peer failed writing ${file.name}")
                        }
                    }
                }

                if (isCancelled) {
                    _transferProgress.value = _transferProgress.value.copy(
                        state = TransferState.CANCELLED,
                        errorMessage = "Transfer cancelled"
                    )
                    _events.emit(TransferEvent.Failed(sessionId, targetIp, "Cancelled by user"))
                } else {
                    val duration = System.currentTimeMillis() - startTime
                    _transferProgress.value = TransferProgress(
                        transferId = sessionId,
                        currentFileIndex = files.size,
                        totalFiles = files.size,
                        overallBytesTransferred = overallTransferred,
                        overallTotalBytes = totalBytes,
                        state = TransferState.COMPLETED
                    )
                    _events.emit(
                        TransferEvent.Completed(
                            transferId = sessionId,
                            peerName = targetIp,
                            totalBytes = overallTransferred,
                            filesCount = files.size,
                            durationMillis = duration
                        )
                    )
                }

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Send error", e)
                    _transferProgress.value = _transferProgress.value.copy(
                        state = TransferState.FAILED,
                        errorMessage = e.message ?: "Connection error"
                    )
                    _events.emit(TransferEvent.Failed(sessionId, targetIp, e.message ?: "Send error"))
                }
            } finally {
                try { dis?.close() } catch (_: Exception) {}
                try { dos?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun pauseTransfer() {
        isPaused = true
    }

    fun resumeTransfer() {
        isPaused = false
    }

    fun cancelTransfer() {
        isCancelled = true
        isPaused = false
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        transferJob?.cancel()
        _transferProgress.value = _transferProgress.value.copy(
            state = TransferState.CANCELLED,
            errorMessage = "Cancelled by user"
        )
    }

    fun resetState() {
        _transferProgress.value = TransferProgress("")
    }
}
