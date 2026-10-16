package com.example.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiDirectManager(private val context: Context) {
    private val TAG = "WifiDirectManager"

    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private var isReceiverRegistered = false

    private val _p2pDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val p2pDevices: StateFlow<List<DiscoveredDevice>> = _p2pDevices.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isP2pEnabled = MutableStateFlow(false)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val p2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    try {
                        channel?.let { ch ->
                            wifiP2pManager?.requestPeers(ch) { peers: WifiP2pDeviceList? ->
                                val deviceList = mutableListOf<DiscoveredDevice>()
                                peers?.deviceList?.forEach { dev ->
                                    deviceList.add(
                                        DiscoveredDevice(
                                            id = dev.deviceAddress,
                                            name = dev.deviceName.ifBlank { "Wi-Fi Direct Peer" },
                                            ipAddress = "", // Assigned after connection
                                            port = 8888,
                                            transportType = TransportType.WIFI_DIRECT
                                        )
                                    )
                                }
                                _p2pDevices.value = deviceList
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting peers", e)
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }
                    if (networkInfo?.isConnected == true) {
                        channel?.let { ch ->
                            wifiP2pManager?.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                                _connectionInfo.value = info
                            }
                        }
                    } else {
                        _connectionInfo.value = null
                    }
                }
            }
        }
    }

    init {
        try {
            channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WifiP2pManager", e)
        }
    }

    fun register() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try {
            context.registerReceiver(p2pReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receiver", e)
        }
    }

    fun unregister() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(p2pReceiver)
            isReceiverRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val ch = channel ?: return
        try {
            wifiP2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _isDiscovering.value = true
                }

                override fun onFailure(reasonCode: Int) {
                    _isDiscovering.value = false
                    Log.e(TAG, "discoverPeers failed: $reasonCode")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting peer discovery", e)
        }
    }

    fun stopDiscovery() {
        val ch = channel ?: return
        try {
            wifiP2pManager?.stopPeerDiscovery(ch, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping discovery", e)
        }
        _isDiscovering.value = false
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, onResult: (Boolean) -> Unit) {
        val ch = channel ?: return onResult(false)
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        try {
            wifiP2pManager?.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onResult(true)
                }

                override fun onFailure(reason: Int) {
                    onResult(false)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Connect error", e)
            onResult(false)
        }
    }
}
