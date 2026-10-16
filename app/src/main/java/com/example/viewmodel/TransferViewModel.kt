package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.QuickTransferApp
import com.example.data.db.TransferFileEntity
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.PairingSession
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferFileItem
import com.example.domain.model.TransferProgress
import com.example.domain.model.TransferState
import com.example.domain.model.TransportType
import com.example.file.FileManager
import com.example.network.FileTransferMeta
import com.example.network.NetworkUtils
import com.example.network.TransferEvent
import com.example.security.PairingSecurity
import com.example.service.TransferForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class IncomingRequestUi(
    val sessionId: String,
    val senderName: String,
    val senderIp: String,
    val files: List<FileTransferMeta>,
    val totalBytes: Long,
    val onAccept: () -> Unit,
    val onReject: () -> Unit
)

class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuickTransferApp
    private val repository = app.transferRepository
    private val preferencesRepo = app.preferencesRepository
    private val fileManager = app.fileManager
    private val nsdManager = app.nsdDiscoveryManager
    private val wifiDirectManager = app.wifiDirectManager
    private val socketEngine = app.socketEngine

    val preferences = preferencesRepo.preferencesFlow

    // Selected files for sending
    private val _selectedFiles = MutableStateFlow<List<TransferFileItem>>(emptyList())
    val selectedFiles: StateFlow<List<TransferFileItem>> = _selectedFiles.asStateFlow()

    // Discovery
    val nsdDevices = nsdManager.discoveredDevices
    val p2pDevices = wifiDirectManager.p2pDevices
    val isScanning = nsdManager.isScanning
    val isAdvertising = nsdManager.isAdvertising

    // Combined discovered devices list
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Active transfer progress
    val transferProgress: StateFlow<TransferProgress> = socketEngine.transferProgress

    // Incoming request dialog/screen state
    private val _incomingRequest = MutableStateFlow<IncomingRequestUi?>(null)
    val incomingRequest: StateFlow<IncomingRequestUi?> = _incomingRequest.asStateFlow()

    // Pairing Session for QR Code
    private val _currentPairingSession = MutableStateFlow<PairingSession?>(null)
    val currentPairingSession: StateFlow<PairingSession?> = _currentPairingSession.asStateFlow()

    // Last completed/failed summary for result screens
    private val _lastTransferSummary = MutableStateFlow<TransferSummary?>(null)
    val lastTransferSummary: StateFlow<TransferSummary?> = _lastTransferSummary.asStateFlow()

    private var currentDbTransferId: Long = 0L
    private var activeDirection: TransferDirection = TransferDirection.SEND
    private var activePeerName: String = ""
    private var activePeerIp: String = ""
    private var autoStopJob: Job? = null

    init {
        // Collect network events
        viewModelScope.launch {
            socketEngine.events.collectLatest { event ->
                when (event) {
                    is TransferEvent.IncomingRequest -> {
                        _incomingRequest.value = IncomingRequestUi(
                            sessionId = event.sessionId,
                            senderName = event.senderName,
                            senderIp = event.senderIp,
                            files = event.files,
                            totalBytes = event.totalBytes,
                            onAccept = {
                                _incomingRequest.value = null
                                activeDirection = TransferDirection.RECEIVE
                                activePeerName = event.senderName
                                activePeerIp = event.senderIp
                                viewModelScope.launch {
                                    currentDbTransferId = repository.recordTransferStart(
                                        sessionId = event.sessionId,
                                        peerName = event.senderName,
                                        peerAddress = event.senderIp,
                                        direction = "RECEIVE",
                                        totalBytes = event.totalBytes,
                                        files = event.files.map {
                                            TransferFileEntity(
                                                transferRecordId = 0,
                                                fileName = it.name,
                                                fileSize = it.size,
                                                mimeType = it.mimeType,
                                                localPath = "",
                                                status = "COMPLETED",
                                                checksumSha256 = it.checksumSha256
                                            )
                                        }
                                    )
                                    TransferForegroundService.startService(app, event.files.firstOrNull()?.name ?: "File")
                                }
                                event.onDecision(true)
                            },
                            onReject = {
                                _incomingRequest.value = null
                                event.onDecision(false)
                            }
                        )
                    }
                    is TransferEvent.Completed -> {
                        TransferForegroundService.stop(app)
                        _lastTransferSummary.value = TransferSummary(
                            transferId = event.transferId,
                            peerName = activePeerName.ifBlank { event.peerName },
                            totalBytes = event.totalBytes,
                            fileCount = event.filesCount,
                            durationMillis = event.durationMillis,
                            direction = activeDirection,
                            isSuccess = true
                        )
                        viewModelScope.launch {
                            if (currentDbTransferId > 0) {
                                repository.updateTransferFinish(
                                    currentDbTransferId,
                                    status = "COMPLETED",
                                    transferredBytes = event.totalBytes,
                                    durationMillis = event.durationMillis
                                )
                            }
                        }
                    }
                    is TransferEvent.Failed -> {
                        TransferForegroundService.stop(app)
                        _lastTransferSummary.value = TransferSummary(
                            transferId = event.transferId,
                            peerName = activePeerName.ifBlank { event.peerName },
                            totalBytes = transferProgress.value.overallBytesTransferred,
                            fileCount = transferProgress.value.totalFiles,
                            durationMillis = 0L,
                            direction = activeDirection,
                            isSuccess = false,
                            errorMessage = event.error
                        )
                        viewModelScope.launch {
                            if (currentDbTransferId > 0) {
                                repository.updateTransferFinish(
                                    currentDbTransferId,
                                    status = "FAILED",
                                    transferredBytes = transferProgress.value.overallBytesTransferred,
                                    durationMillis = 0L,
                                    error = event.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // Monitor progress to update notification
        viewModelScope.launch {
            socketEngine.transferProgress.collectLatest { p ->
                if (p.state == TransferState.TRANSFERRING && p.overallTotalBytes > 0) {
                    val percent = ((p.overallBytesTransferred * 100) / p.overallTotalBytes).toInt().coerceIn(0, 100)
                    val speedText = FileManager.formatSpeed(p.speedBytesPerSec)
                    TransferForegroundService.updateProgress(app, percent, speedText, p.currentFileName)
                }
            }
        }

        // Merge discovery sources
        viewModelScope.launch {
            nsdDevices.collectLatest { list ->
                val p2p = p2pDevices.value
                val combined = (list + p2p).distinctBy { it.id }
                _discoveredDevices.value = combined
            }
        }
        viewModelScope.launch {
            p2pDevices.collectLatest { list ->
                val nsd = nsdDevices.value
                val combined = (nsd + list).distinctBy { it.id }
                _discoveredDevices.value = combined
            }
        }
    }

    // --- File Selection ---

    fun addSelectedUris(uris: List<Uri>) {
        val current = _selectedFiles.value.toMutableList()
        for (uri in uris) {
            val item = fileManager.resolveUriMetadata(uri)
            if (current.none { it.uri == uri }) {
                current.add(item)
            }
        }
        _selectedFiles.value = current
    }

    fun removeSelectedFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != id }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    // --- Discovery & Receiving Mode ---

    fun startReceivingMode() {
        val prefs = preferences.value
        val localIp = NetworkUtils.getLocalIpAddress()
        val port = prefs.serverPort

        // Start socket server
        socketEngine.startServer(
            port = port,
            expectedPin = "",
            autoAccept = prefs.autoAcceptKnownPeers
        )

        // Advertise over NSD
        nsdManager.startAdvertising(prefs.deviceName, port)

        // Generate QR Pairing session
        val session = PairingSecurity.generateSession(prefs.deviceName, localIp, port)
        _currentPairingSession.value = session

        // Schedule auto-stop if configured
        val autoStopMinutes = prefs.autoStopDiscoveryMinutes
        autoStopJob?.cancel()
        if (autoStopMinutes > 0) {
            autoStopJob = viewModelScope.launch {
                delay(autoStopMinutes * 60 * 1000L)
                stopReceivingMode()
            }
        }
    }

    fun stopReceivingMode() {
        autoStopJob?.cancel()
        nsdManager.stopAdvertising()
        socketEngine.stopServer()
        _currentPairingSession.value = null
    }

    fun startScanning() {
        nsdManager.startDiscovery()
        wifiDirectManager.register()
        wifiDirectManager.startDiscovery()
    }

    fun stopScanning() {
        nsdManager.stopDiscovery()
        wifiDirectManager.stopDiscovery()
        wifiDirectManager.unregister()
    }

    fun refreshScanning() {
        stopScanning()
        startScanning()
    }

    // --- Sending ---

    fun sendToDevice(device: DiscoveredDevice) {
        val files = _selectedFiles.value
        if (files.isEmpty()) return

        activeDirection = TransferDirection.SEND
        activePeerName = device.name
        activePeerIp = device.ipAddress

        viewModelScope.launch {
            currentDbTransferId = repository.recordTransferStart(
                sessionId = java.util.UUID.randomUUID().toString(),
                peerName = device.name,
                peerAddress = device.ipAddress,
                direction = "SEND",
                totalBytes = files.sumOf { it.size },
                files = files.map {
                    TransferFileEntity(
                        transferRecordId = 0,
                        fileName = it.name,
                        fileSize = it.size,
                        mimeType = it.mimeType,
                        localPath = it.uri.toString(),
                        status = "COMPLETED",
                        checksumSha256 = ""
                    )
                }
            )
            TransferForegroundService.startService(app, files.firstOrNull()?.name ?: "File")
            socketEngine.sendFiles(
                targetIp = device.ipAddress,
                targetPort = device.port,
                senderName = preferences.value.deviceName,
                files = files
            )
        }
    }

    fun connectAndSendViaSession(session: PairingSession) {
        val device = DiscoveredDevice(
            id = "${session.ipAddress}:${session.port}",
            name = session.deviceName,
            ipAddress = session.ipAddress,
            port = session.port,
            transportType = TransportType.WIFI_LOCAL
        )
        sendToDevice(device)
    }

    fun pauseTransfer() {
        socketEngine.pauseTransfer()
    }

    fun resumeTransfer() {
        socketEngine.resumeTransfer()
    }

    fun cancelTransfer() {
        TransferForegroundService.stop(app)
        socketEngine.cancelTransfer()
    }

    fun resetTransferState() {
        socketEngine.resetState()
        _lastTransferSummary.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopReceivingMode()
        stopScanning()
    }
}

data class TransferSummary(
    val transferId: String,
    val peerName: String,
    val totalBytes: Long,
    val fileCount: Int,
    val durationMillis: Long,
    val direction: TransferDirection,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)
